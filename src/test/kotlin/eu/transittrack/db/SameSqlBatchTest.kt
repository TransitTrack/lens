package eu.transittrack.db

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.AvlFormat
import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [execBatch] sends its first statement's SQL for every statement, so a batch whose statements
 * render different SQL must fail. The full SQL comparison only runs with JVM assertions on, which
 * Gradle's test JVM has.
 */
@PostgresSliceTest
class SameSqlBatchTest(
    @Autowired val db: Database,
) : PostgresPerMethodTest() {
    @Test
    fun testsRunWithAssertionsEnabled() {
        assertThat(Class.forName("eu.transittrack.db.SameSqlBatch").desiredAssertionStatus()).isTrue()
    }

    /** Same parameter types, different SQL: only the full check catches it. */
    @Test
    fun aBatchWhoseStatementsRenderDifferentSqlFails() {
        val t = AvlFeedTable
        val statements =
            listOf(
                buildStatement { t.update({ t.code eq "a" }) { it[t.name] = "x" } },
                buildStatement { t.update({ t.code neq "b" }) { it[t.name] = "y" } },
            )
        transaction(db) {
            assertFailure { execBatch(statements) }
                .isInstanceOf<IllegalStateException>()
                .hasMessage("A batch's statements must all render the same SQL")
            rollback()
        }
    }

    /** Different parameter types fail even without assertions. */
    @Test
    fun aBatchWhoseStatementsTakeDifferentParametersFails() {
        val t = AvlFeedTable
        val statements =
            listOf(
                buildStatement { t.update({ t.code eq "a" }) { it[t.name] = "x" } },
                buildStatement { t.update({ t.code eq "b" }) { it[t.format] = AvlFormat.GTFS_RT } },
            )
        transaction(db) {
            assertFailure { execBatch(statements) }
                .isInstanceOf<IllegalStateException>()
                .hasMessage("A batch's statements must all take the same parameters")
            rollback()
        }
    }
}
