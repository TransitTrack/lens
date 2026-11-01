package eu.transittrack.db

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.BatchUpsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BatchInsertBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Exposed's `batchInsert(data, shouldReturnGeneratedValues = false)` for callers that never read what it
 * returns: the same single JDBC batch and the same SQL. Exposed's own version still builds a [org.jetbrains.exposed.v1.core.ResultRow]
 * for every inserted row (its values, defaults and nulls, each in fresh maps) and walks every row's
 * arguments three times; on the AVL and prediction hot paths that was most of what a write allocated.
 * This one walks the arguments once and builds no rows.
 *
 * Every row must set the same columns. Where Exposed would split such a batch in two, this throws
 * Exposed's `BatchDataInconsistentException` instead.
 */
fun <T : Table, E> T.batchInsertWithoutResults(
    data: Iterable<E>,
    body: BatchInsertStatement.(E) -> Unit,
) = executeWithoutResults(BatchInsertStatement(this, false, false), data, body)

/**
 * Exposed's `batchUpsert(data, *keys, onUpdate, where = ..., shouldReturnGeneratedValues = false)`
 * without building the rows it would return. See [batchInsertWithoutResults].
 */
fun <T : Table, E> T.batchUpsertWithoutResults(
    data: Iterable<E>,
    vararg keys: Column<*>,
    onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
    where: (() -> Op<Boolean>)? = null,
    body: BatchUpsertStatement.(E) -> Unit,
) = executeWithoutResults(
    buildStatement { batchUpsert(null, onUpdate, null, where, false, keys = keys, body = body) },
    data,
    body,
)

private fun <S : BatchInsertStatement, E> executeWithoutResults(
    statement: S,
    data: Iterable<E>,
    body: S.(E) -> Unit,
) {
    val rows = data.iterator()
    if (!rows.hasNext()) return
    rows.forEach {
        statement.addBatch()
        statement.body(it)
    }
    @OptIn(InternalApi::class)
    statement.validateLastBatch()
    TransactionManager.current().exec(WithoutResults(statement))
}

/** Runs the batch and records how many rows it wrote, without building the inserted rows. */
private class WithoutResults<S : BatchInsertStatement>(
    statement: S,
) : BatchInsertBlockingExecutable<S>(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        val written = executeBatch().sum()
        statement.insertedCount = written
        @OptIn(InternalApi::class)
        statement.resultedValues = emptyList()
        return written
    }
}
