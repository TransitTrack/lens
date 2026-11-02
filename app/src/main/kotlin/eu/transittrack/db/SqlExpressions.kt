package eu.transittrack.db

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.append
import org.jetbrains.exposed.v1.core.statements.InsertSelectStatement
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
 * `INSERT INTO <table> (<columns>) <selectQuery> ON CONFLICT (<conflictTarget>) DO NOTHING`. Exposed's
 * `insertIgnore` renders `ON CONFLICT DO NOTHING` with no conflict target, which skips a violation of
 * any unique constraint rather than of the one named; this names it.
 */
class InsertSelectOnConflictDoNothing(
    columns: List<Column<*>>,
    selectQuery: AbstractQuery<*>,
    private val conflictTarget: List<Column<*>>,
) : InsertSelectStatement(columns, selectQuery, isIgnore = false) {
    init {
        require(conflictTarget.isNotEmpty()) { "A conflict target needs at least one column" }
    }

    override fun prepareSQL(
        transaction: Transaction,
        prepared: Boolean,
    ): String =
        super.prepareSQL(transaction, prepared) +
            conflictTarget.joinToString(prefix = " ON CONFLICT (", postfix = ") DO NOTHING") { transaction.identity(it) }
}

/**
 * Runs [statements] as one JDBC batch: one prepared statement and one round trip, each statement's
 * arguments executed in order, so each one sees the rows the ones before it wrote. Exposed batches
 * only inserts and upserts on its own; this covers updates and insert-selects too. The statements
 * must render the same SQL, so build them from one DSL expression whose shape doesn't depend on the
 * row (every value a bound parameter): only the first one's SQL is rendered and sent.
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

    /** Rendered once: Exposed renders a statement's whole SQL to collect its arguments. */
    private val argumentSets by lazy { statements.flatMap { it.arguments() } }

    /**
     * Sends the first statement's SQL for every statement. The argument types are always checked:
     * that is cheap and catches a statement with more, fewer or different parameters. The full check
     * that every statement renders the same SQL (which also catches, say, `a = ?` against `a IS NULL`)
     * costs more than the rest of the batch, so it runs only with JVM assertions enabled, as in tests.
     */
    override fun prepareSQL(
        transaction: Transaction,
        prepared: Boolean,
    ): String {
        val types = argumentSets.first().map { it.first }
        check(argumentSets.all { args -> sameTypes(args, types) }) { "A batch's statements must all take the same parameters" }
        val sql = statements.first().prepareSQL(transaction, prepared)
        // Gated by an `if` rather than `assert(...)`: Kotlin's assert always evaluates its argument.
        if (CHECK_SAME_SQL) {
            check(statements.all { it.prepareSQL(transaction, prepared) == sql }) { "A batch's statements must all render the same SQL" }
        }
        return sql
    }

    override fun arguments() = argumentSets

    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): List<Int> = executeBatch()

    private fun sameTypes(
        args: Iterable<Pair<IColumnType<*>, Any?>>,
        types: List<IColumnType<*>>,
    ): Boolean {
        var i = 0
        for ((type, _) in args) {
            if (i >= types.size || types[i] != type) return false
            i++
        }
        return i == types.size
    }

    private companion object {
        /** Whether JVM assertions (`-ea`) are enabled for this class; Gradle's test JVM enables them. */
        val CHECK_SAME_SQL: Boolean = SameSqlBatch::class.java.desiredAssertionStatus()
    }
}
