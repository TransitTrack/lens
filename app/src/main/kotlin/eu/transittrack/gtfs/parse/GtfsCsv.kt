package eu.transittrack.gtfs.parse

import java.io.InputStream
import java.io.Reader
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Streaming RFC 4180 reader for GTFS `.txt` files: quoted fields (with `""` escapes, embedded commas
 * and newlines), `\n` or `\r\n` line endings, and a leading UTF-8 BOM. Records are handed to the
 * callback one at a time and never collected, so memory stays flat regardless of file size.
 */
object GtfsCsv {
    private const val BOM = 0xFEFF

    fun forEachRecord(
        input: InputStream,
        fileName: String,
        action: (GtfsRecord) -> Unit,
    ) {
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            // Drop a leading BOM before tokenising, so a quoted first header cell still parses as quoted.
            reader.mark(1)
            if (reader.read() != BOM) reader.reset()
            val tokenizer = Tokenizer(reader)
            val headerCells = tokenizer.next() ?: return
            val header = LinkedHashMap<String, Int>()
            headerCells.forEachIndexed { i, name -> name.trim().takeIf { it.isNotEmpty() }?.let { header.putIfAbsent(it, i) } }
            while (true) {
                val values = tokenizer.next() ?: break
                if (values.all { it.isBlank() }) continue
                action(GtfsRecord(fileName, tokenizer.recordLine, header, values))
            }
        }
    }

    /** Splits a character stream into records; tracks the physical line each record starts on. */
    private class Tokenizer(
        private val reader: Reader,
    ) {
        private val field = StringBuilder()
        private var pending = -2 // -2 = nothing buffered
        private var physicalLine = 1L

        /** Line the record most recently returned by [next] starts on. */
        var recordLine = 1L
            private set

        private fun read(): Int {
            if (pending != -2) {
                val c = pending
                pending = -2
                return c
            }
            return reader.read()
        }

        fun next(): MutableList<String>? {
            recordLine = physicalLine
            var c = read()
            if (c == -1) return null
            val cells = ArrayList<String>()
            field.setLength(0)
            var quoted = false
            while (true) {
                if (quoted) {
                    when (c) {
                        -1 -> {
                            cells += field.toString()
                            return cells
                        }

                        '"'.code -> {
                            val n = read()
                            if (n == '"'.code) {
                                field.append('"')
                            } else {
                                quoted = false
                                c = n
                                continue
                            }
                        }

                        else -> {
                            if (c == '\n'.code) physicalLine++
                            field.append(c.toChar())
                        }
                    }
                } else {
                    when (c) {
                        -1 -> {
                            cells += field.toString()
                            return cells
                        }

                        ','.code -> {
                            cells += field.toString()
                            field.setLength(0)
                        }

                        '\r'.code -> {
                            val n = read()
                            if (n != '\n'.code) pending = n
                            physicalLine++
                            cells += field.toString()
                            return cells
                        }

                        '\n'.code -> {
                            physicalLine++
                            cells += field.toString()
                            return cells
                        }

                        '"'.code -> {
                            if (field.isEmpty()) quoted = true else field.append('"')
                        }

                        else -> {
                            field.append(c.toChar())
                        }
                    }
                }
                c = read()
            }
        }
    }
}

/** Thrown by a mapper when a row lacks a value its table requires; ingest skips that row. */
class MissingRequiredFieldException(
    val fileName: String,
    val line: Long,
    val column: String,
) : RuntimeException("$fileName:$line missing $column")

/**
 * One data row of a GTFS file. [line] is the 1-based physical line the row starts on.
 *
 * Typed accessors are lenient: an absent column, a blank cell or a value that doesn't parse all
 * yield `null` (the analyzer has already reported malformed values).
 */
class GtfsRecord(
    val fileName: String,
    val line: Long,
    private val header: Map<String, Int>,
    private val values: List<String>,
) {
    /** Trimmed cell value; `null` if the column is absent, the row is short, or the cell is blank. */
    fun raw(column: String): String? =
        header[column]
            ?.let { values.getOrNull(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun string(column: String): String? = raw(column)

    fun int(column: String): Int? = raw(column)?.toIntOrNull()

    fun double(column: String): Double? = raw(column)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** `YYYYMMDD`. */
    fun date(column: String): LocalDate? =
        raw(column)
            ?.takeIf { DATE.matches(it) }
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() }

    /** `H:MM:SS` / `HH:MM:SS` as seconds since midnight; hours may exceed 24. */
    fun time(column: String): Int? =
        raw(column)?.let { TIME.matchEntire(it) }?.destructured?.let { (h, m, s) ->
            h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
        }

    /** Six hex digits, returned as `#RRGGBB` (upper case). */
    fun color(column: String): String? = raw(column)?.takeIf { COLOR.matches(it) }?.let { "#" + it.uppercase() }

    /** BCP 47 language tag. */
    fun locale(column: String): Locale? = raw(column)?.let { Locale.forLanguageTag(it) }?.takeIf { it.toLanguageTag() != "und" }

    /** BCP 47 language tag, normalised. */
    fun language(column: String): String? = locale(column)?.toLanguageTag()

    /** IANA time zone id. */
    fun timezone(column: String): ZoneId? = raw(column)?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** ISO 4217 currency code. */
    fun currency(column: String): String? = raw(column)?.let { runCatching { Currency.getInstance(it).currencyCode }.getOrNull() }

    private companion object {
        val DATE = Regex("""\d{8}""")
        val TIME = Regex("""(\d{1,3}):([0-5]\d):([0-5]\d)""")
        val COLOR = Regex("""[0-9A-Fa-f]{6}""")
    }
}
