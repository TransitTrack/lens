package eu.transittrack.support

import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Raw SQL for the cases the Exposed DSL cannot express (Postgres catalog / information_schema
 * probes). [params] are bound as strings to the `?` placeholders of [sql].
 */
fun <T> Database.catalogQuery(
    sql: String,
    vararg params: String,
    map: (java.sql.ResultSet) -> T,
): T? = transaction(this) { exec(sql, params.map { VarCharColumnType() to it }) { rs -> map(rs) } }

/** The first column of the first row of [sql] as a [Long]. */
fun Database.catalogLong(
    sql: String,
    vararg params: String,
): Long =
    catalogQuery(sql, *params) { rs ->
        rs.next()
        rs.getLong(1)
    }!!

/** The first column of the first row of [sql] as a [String]. */
fun Database.catalogString(
    sql: String,
    vararg params: String,
): String =
    catalogQuery(sql, *params) { rs ->
        rs.next()
        rs.getString(1)
    }!!
