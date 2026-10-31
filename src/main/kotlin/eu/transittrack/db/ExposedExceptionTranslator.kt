package eu.transittrack.db

import java.sql.SQLException

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.springframework.dao.DataAccessException
import org.springframework.dao.support.PersistenceExceptionTranslator
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator
import org.springframework.stereotype.Component

/**
 * Turns Exposed's [ExposedSQLException] into Spring's [DataAccessException] hierarchy, so code
 * built on Exposed throws the same `DataIntegrityViolationException`/`DuplicateKeyException`/...
 * that callers and tests already expect from JPA.
 *
 * [ExposedSQLException] is a checked [SQLException], and Spring's `@Repository` translation
 * interceptor only looks at [RuntimeException]s, so [translateExceptionIfPossible] handles a
 * runtime exception that carries one in its cause chain, while [translate] is the direct entry
 * point for code that catches the [SQLException] itself.
 */
@Component
class ExposedExceptionTranslator : PersistenceExceptionTranslator {
    // Falls back to SQLStateSQLExceptionTranslator for driver exceptions that aren't JDBC 4
    // subclasses (PSQLException is a plain SQLException carrying only a SQLState).
    private val delegate = SQLExceptionSubclassTranslator()

    override fun translateExceptionIfPossible(ex: RuntimeException): DataAccessException? =
        generateSequence(ex.cause) { it.cause }
            .filterIsInstance<ExposedSQLException>()
            .firstOrNull()
            ?.let(::translate)

    fun translate(ex: SQLException): DataAccessException {
        val sql = (ex as? ExposedSQLException)?.causedByQueries()?.joinToString("; ")
        // ExposedSQLException wraps the driver's exception; translate the driver's own SQLState.
        val cause = (ex as? ExposedSQLException)?.cause as? SQLException ?: ex
        return delegate.translate("Exposed", sql, cause)
            ?: UncategorizedSQLException("Exposed", sql, cause)
    }
}
