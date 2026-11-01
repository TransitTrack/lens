package eu.transittrack.db

import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi

/**
 * `extract(epoch from until - since)`: the seconds between two `timestamptz` expressions (as HQL's
 * `timestampdiff(second, since, until)` renders on Postgres). Exposed ships no `extract(field from
 * ...)` builder, so this one expression is spelled out here.
 */
class EpochSecondsBetween(
    private val since: Expression<*>,
    private val until: Expression<*>,
) : ExpressionWithColumnType<Double>() {
    override val columnType = DoubleColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) =
        queryBuilder {
            append("extract(epoch from ", until, "-", since, ")")
        }
}

/**
 * Runs [statements] as one JDBC batch: one prepared statement and one round trip, each statement's
 * arguments executed in order, so each one sees the rows the ones before it wrote. Exposed batches
 * only inserts and upserts on its own; this covers updates and insert-selects too. The statements
 * must render the same SQL, so build them from one DSL expression whose shape doesn't depend on the
 * row (every value a bound parameter).
 */
fun JdbcTransaction.execBatch(statements: List<Statement<*>>) {
    if (statements.isEmpty()) return
    exec(SameSqlBatch(statements))
}

private class SameSqlBatch(
    private val statements: List<Statement<*>>,
) : Statement<List<Int>>(statements.first().type, statements.first().targets),
    BlockingExecutable<List<Int>, SameSqlBatch> {
    override val statement: SameSqlBatch get() = this

    override val isAlwaysBatch: Boolean get() = true

    override fun prepareSQL(
        transaction: Transaction,
        prepared: Boolean,
    ): String {
        val sql = statements.first().prepareSQL(transaction, prepared)
        check(statements.all { it.prepareSQL(transaction, prepared) == sql }) { "A batch's statements must all render the same SQL" }
        return sql
    }

    override fun arguments() = statements.flatMap { it.arguments() }

    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): List<Int> = executeBatch()
}
