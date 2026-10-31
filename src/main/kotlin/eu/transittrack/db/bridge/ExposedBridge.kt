package eu.transittrack.db.bridge

import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import jakarta.persistence.EntityManagerFactory

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelTransaction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.orm.jpa.EntityManagerFactoryUtils
import org.springframework.transaction.support.TransactionSynchronizationManager

import eu.transittrack.db.ExposedExceptionTranslator

/*
 * Temporary JPA <-> Exposed bridge, deleted at the cutover to Exposed's own Spring transaction
 * manager. Until then JPA's transaction manager owns every Spring transaction, and this lets
 * Exposed statements run on the JDBC connection of the active JPA transaction (so they commit and
 * roll back with it and see its flushed writes), or in an Exposed transaction of their own when no
 * Spring transaction is active.
 */

/**
 * Marks a class whose methods run Exposed statements; [ExposedBridgeAspect] wraps every one of its
 * methods in an Exposed transaction. Temporary — deleted at the cutover.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExposedBridge

/**
 * A view of the active Spring transaction's connection that Exposed may use but not end: commit,
 * rollback and the transaction settings belong to the JPA transaction (and Postgres rejects an
 * isolation change mid-transaction), so they are no-ops. [close] hands the connection back
 * through [release], which never closes a transaction-bound connection.
 */
class NonClosingConnection(
    val delegate: Connection,
    private val release: (Connection) -> Unit,
) : Connection by delegate {
    override fun close() = release(delegate)

    override fun commit() = Unit

    override fun rollback() = Unit

    override fun setAutoCommit(autoCommit: Boolean) = Unit

    override fun setTransactionIsolation(level: Int) = Unit

    override fun setReadOnly(readOnly: Boolean) = Unit
}

@Configuration(proxyBeanMethods = false)
class ExposedBridgeConfig {
    /**
     * Exposed's own transactions on pooled connections, for work outside any Spring transaction.
     * A bean so code that needs a [Database] outright (e.g. lock providers) can inject it.
     */
    @Bean
    fun exposedDatabase(dataSource: DataSource): Database = Database.connect(dataSource, databaseConfig = bridgeDatabaseConfig())

    @Bean
    fun exposedBridgeAspect(
        dataSource: DataSource,
        entityManagerFactory: EntityManagerFactory,
        exposedDatabase: Database,
        translator: ExposedExceptionTranslator,
    ): ExposedBridgeAspect {
        // Not a bean: only usable inside a Spring transaction, so only the aspect should reach it.
        val boundDb =
            Database.connect(
                getNewConnection = {
                    NonClosingConnection(DataSourceUtils.getConnection(dataSource)) {
                        DataSourceUtils.releaseConnection(it, dataSource)
                    }
                },
                databaseConfig = bridgeDatabaseConfig(),
            )
        return ExposedBridgeAspect(dataSource, entityManagerFactory, exposedDatabase, boundDb, translator)
    }

    // A failed statement must not re-run the whole intercepted method: inside a JPA transaction
    // Postgres has already aborted it (a retry would only surface "current transaction is
    // aborted" instead of the real error), and outside one the method may not be idempotent.
    private fun bridgeDatabaseConfig() = DatabaseConfig { defaultMaxAttempts = 1 }
}

/**
 * Runs each method of an [ExposedBridge] class in the right Exposed transaction:
 * - inside a Spring (JPA) transaction: on that transaction's connection, after flushing the
 *   persistence context so Exposed sees pending JPA writes — unless an Exposed transaction on that
 *   same connection is already open (a nested bridged call), which is reused;
 * - outside one: reuses the current Exposed transaction if there is one, else opens its own on
 *   [plainDb], which commits when the method returns.
 *
 * Ordered last, so it runs inside Spring's transaction interceptor: a `REQUIRES_NEW` method has
 * already got its new transaction (and connection) by the time the bridge picks one. Spring's
 * transaction advisor shares the default lowest precedence, and among equal orders Spring sorts
 * its advisor beans ahead of `@Aspect` advice, so this stays the innermost.
 *
 * SQL failures leave as Spring [org.springframework.dao.DataAccessException]s — unchecked, so
 * the surrounding Spring transaction rolls back on them.
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
class ExposedBridgeAspect(
    private val dataSource: DataSource,
    private val entityManagerFactory: EntityManagerFactory,
    private val plainDb: Database,
    private val boundDb: Database,
    private val translator: ExposedExceptionTranslator,
) {
    @Around("@within(eu.transittrack.db.bridge.ExposedBridge)")
    fun around(pjp: ProceedingJoinPoint): Any? =
        try {
            route(pjp)
        } catch (e: SQLException) {
            throw translator.translate(e)
        }

    private fun route(pjp: ProceedingJoinPoint): Any? {
        val current = TransactionManager.currentOrNull()

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // A bound transaction belongs to a Spring transaction that has been suspended
            // (e.g. NOT_SUPPORTED); don't keep writing into it.
            return if (current != null && current.db !== boundDb) {
                pjp.proceed()
            } else {
                inTopLevelTransaction(plainDb) { pjp.proceed() }
            }
        }

        val springConnection =
            checkNotNull((TransactionSynchronizationManager.getResource(dataSource) as? ConnectionHolder)?.connection) {
                "An actual Spring transaction is active but no JDBC connection is bound to the DataSource"
            }
        val currentConnection = (current?.takeIf { it.db === boundDb }?.connection?.connection as? NonClosingConnection)?.delegate
        if (currentConnection === springConnection) return pjp.proceed()

        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory)?.flush()
        }
        return inTopLevelTransaction(boundDb) {
            // Bind the connection now rather than on the first statement, so it is this Spring
            // transaction's even if a nested call opens another one first.
            connection
            pjp.proceed()
        }
    }
}
