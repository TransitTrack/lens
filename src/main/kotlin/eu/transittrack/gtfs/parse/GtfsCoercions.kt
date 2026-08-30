package eu.transittrack.gtfs.parse

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GtfsParseException(message: String) : RuntimeException(message)

object GtfsCoercions {

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE // yyyyMMdd

    fun secondsOfDay(text: String): Int {
        val parts = text.trim().split(":")
        if (parts.size != 3) throw GtfsParseException("bad time '$text'")
        return try {
            val h = parts[0].toInt(); val m = parts[1].toInt(); val s = parts[2].toInt()
            if (m !in 0..59 || s !in 0..59 || h < 0) throw GtfsParseException("bad time '$text'")
            h * 3600 + m * 60 + s
        } catch (e: NumberFormatException) {
            throw GtfsParseException("bad time '$text'")
        }
    }

    fun gtfsDate(text: String): LocalDate =
        try { LocalDate.parse(text.trim(), DATE_FMT) }
        catch (e: Exception) { throw GtfsParseException("bad date '$text'") }
}
