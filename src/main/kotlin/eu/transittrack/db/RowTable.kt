package eu.transittrack.db

import java.time.Instant
import java.time.ZoneOffset

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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

    /** A SMALLINT column holding [E]'s ordinal, as JPA's default `@Enumerated` (ORDINAL) mapping stores it. */
    protected inline fun <reified E : Enum<E>> shortOrdinal(name: String): Column<E> {
        val values = enumValues<E>()
        return short(name).transform({ values[it.toInt()] }, { it.ordinal.toShort() })
    }

    /**
     * A `timestamptz` column read and written as an [Instant], bound as a UTC `OffsetDateTime`.
     * Exposed's `timestamp()` binds through a `java.sql.Timestamp` built from the JVM zone's local
     * time, which shifts instants inside a DST fall-back hour by the overlap; this never consults
     * the JVM zone.
     */
    protected fun timestampTz(name: String): Column<Instant> =
        timestampWithTimeZone(name).transform({ it.toInstant() }, { it.atOffset(ZoneOffset.UTC) })

    /** The column's database default is `now()`. */
    protected fun Column<Instant>.defaultNow(): Column<Instant> = defaultExpression(CustomFunction("now", columnType))
}
