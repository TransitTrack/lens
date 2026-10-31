package eu.transittrack.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

/** An Exposed table that maps its rows to and from one row type [R]. */
abstract class RowTable<R : Any>(
    name: String,
) : Table(name) {
    abstract fun read(row: ResultRow): R

    /** Sets every column of [row] on [s] except `id` (database-generated, or set by the caller). */
    abstract fun bind(
        s: UpdateBuilder<*>,
        row: R,
    )
}
