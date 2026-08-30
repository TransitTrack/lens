package eu.transittrack.gtfs.parse

import java.time.LocalDate

/**
 * A single GTFS CSV record, keyed by header column name.
 *
 * Scope for now: raw string access only. Typed accessors (int/double/bool01/date/seconds)
 * are layered on in a later task.
 */
class GtfsRow(private val values: Map<String, String?>) {

    /** All header column names present in the source file. */
    val columns: Set<String> get() = values.keys.toSet()

    /** Raw value for [col], or `null` if the column is absent or its value is blank. */
    fun str(col: String): String? = values[col]?.takeIf { it.isNotEmpty() }

    fun int(col: String): Int? = str(col)?.let {
        it.toIntOrNull() ?: throw GtfsParseException("bad int '$it' in column '$col'")
    }

    fun double(col: String): Double? = str(col)?.let {
        it.toDoubleOrNull() ?: throw GtfsParseException("bad number '$it' in column '$col'")
    }

    fun bool01(col: String): Boolean? = when (str(col)) {
        null -> null
        "0" -> false
        "1" -> true
        else -> throw GtfsParseException("bad boolean '${str(col)}' in column '$col'")
    }

    fun date(col: String): LocalDate? = str(col)?.let { GtfsCoercions.gtfsDate(it) }

    fun seconds(col: String): Int? = str(col)?.let { GtfsCoercions.secondsOfDay(it) }
}
