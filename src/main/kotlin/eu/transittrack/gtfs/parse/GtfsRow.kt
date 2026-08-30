package eu.transittrack.gtfs.parse

/**
 * A single GTFS CSV record, keyed by header column name.
 *
 * Scope for now: raw string access only. Typed accessors (int/double/bool01/date/seconds)
 * are layered on in a later task.
 */
class GtfsRow(private val values: Map<String, String?>) {

    /** All header column names present in the source file. */
    val columns: Set<String> get() = values.keys

    /** Raw value for [col], or `null` if the column is absent or its value is blank. */
    fun str(col: String): String? = values[col]?.takeIf { it.isNotEmpty() }
}
