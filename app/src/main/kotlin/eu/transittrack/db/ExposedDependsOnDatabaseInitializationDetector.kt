package eu.transittrack.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDependsOnDatabaseInitializationDetector

/**
 * Makes Exposed's transaction manager and [Database] beans wait for database initialization
 * (Liquibase), as Boot already does for `JdbcTemplate`: beans that query while they are being
 * created (e.g. `AvlPoller`) must see the migrated schema. Registered in `META-INF/spring.factories`.
 */
class ExposedDependsOnDatabaseInitializationDetector : AbstractBeansOfTypeDependsOnDatabaseInitializationDetector() {
    override fun getDependsOnDatabaseInitializationBeanTypes(): Set<Class<*>> =
        setOf(SpringTransactionManager::class.java, Database::class.java)
}
