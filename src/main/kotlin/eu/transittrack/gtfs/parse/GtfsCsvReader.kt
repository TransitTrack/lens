package eu.transittrack.gtfs.parse

import org.apache.commons.csv.CSVFormat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets

/**
 * Streaming reader for GTFS `.txt` (CSV) files.
 *
 * Behaviour:
 * - header-keyed: each row is a [GtfsRow] keyed by the first line's column names;
 * - UTF-8, with a leading UTF-8 BOM stripped so the first header stays usable;
 * - RFC 4180 quoting (embedded commas and newlines inside quotes are preserved);
 * - surrounding whitespace is trimmed and empty values become `null`;
 * - empty lines are ignored; CRLF and LF line endings are both accepted.
 *
 * The returned [Sequence] is lazy and reads from [input] on demand. The caller must
 * fully consume it before the underlying stream is closed.
 */
object GtfsCsvReader {

    private val FORMAT: CSVFormat = CSVFormat.RFC4180.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreEmptyLines(true)
        .setTrim(true)
        .build()

    fun <T> read(input: InputStream, mapper: (GtfsRow) -> T): Sequence<T> {
        val reader = BufferedReader(InputStreamReader(stripBom(input), StandardCharsets.UTF_8))
        val parser = FORMAT.parse(reader)
        val headers = parser.headerNames
        return parser.asSequence().map { record ->
            val map = HashMap<String, String?>(headers.size)
            for (name in headers) {
                map[name] = if (record.isMapped(name)) record.get(name) else null
            }
            mapper(GtfsRow(map))
        }
    }

    private fun stripBom(input: InputStream): InputStream {
        val pushback = PushbackInputStream(input, 3)
        // readNBytes loops until 3 bytes or EOF; a single read(...) can return fewer
        // (e.g. ZipInputStream hands back 1-2 byte chunks), which previously left the
        // BOM in the stream and glued it onto the first header name.
        val prefix = pushback.readNBytes(3)
        val isBom = prefix.size == 3 &&
            prefix[0] == 0xEF.toByte() &&
            prefix[1] == 0xBB.toByte() &&
            prefix[2] == 0xBF.toByte()
        if (!isBom && prefix.isNotEmpty()) pushback.unread(prefix)
        return pushback
    }
}
