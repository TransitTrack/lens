package eu.transittrack.db

import java.sql.SQLException

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionSystemException

/**
 * Makes SQL failures leave every transactional method as Spring
 * [org.springframework.dao.DataAccessException]s (`DataIntegrityViolationException`,
 * `DuplicateKeyException`, ...), wherever they surface:
 * - on a statement: Exposed throws [org.jetbrains.exposed.v1.exceptions.ExposedSQLException], a
 *   checked [SQLException], which Spring's `@Repository` exception translation never looks at
 *   (it only handles runtime exceptions);
 * - at commit (e.g. a deferred constraint): Exposed's
 *   [org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager] wraps the driver's
 *   [SQLException] in a [TransactionSystemException].
 *
 * Matches every `@Transactional` class or method — the boundaries Exposed statements run inside.
 * Ordered first, so it runs outside Spring's transaction interceptor and sees what the commit
 * throws; that interceptor still rolls back on the raw [SQLException], since the starter's
 * `ExposedSpringTransactionAttributeSource` adds it to the rollback rules.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExposedExceptionTranslationAspect(
    private val translator: ExposedExceptionTranslator,
) {
    @Around(
        "@within(org.springframework.transaction.annotation.Transactional) || " +
            "@annotation(org.springframework.transaction.annotation.Transactional)",
    )
    fun translate(pjp: ProceedingJoinPoint): Any? =
        try {
            pjp.proceed()
        } catch (e: SQLException) {
            throw translator.translate(e)
        } catch (e: TransactionSystemException) {
            throw (e.cause as? SQLException)?.let(translator::translate) ?: e
        }
}
