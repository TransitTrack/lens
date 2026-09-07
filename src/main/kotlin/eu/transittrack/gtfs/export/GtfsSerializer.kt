package eu.transittrack.gtfs.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.sql.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component

/**
 * Streams a revision's RAW GTFS tables to a `.zip` — one CSV per [GtfsFileSchema.FILES] entry
 * that has at least one row. Rows stream out of the DB one at a time via [JdbcTemplate.query]'s
 * `RowCallbackHandler` overload.
 */
@Component
class GtfsSerializer(
    private val jdbc: JdbcTemplate,
) {
    fun serialize(
        revisionId: Long,
        out: OutputStream,
    ) {
        ZipOutputStream(out).use { zip ->
            for (file in GtfsFileSchema.FILES) {
                if (rowCount(file.table, revisionId) == 0L) continue
                zip.putNextEntry(ZipEntry(file.name))
                val w = OutputStreamWriter(zip, Charsets.UTF_8)
                w.write(file.columns.joinToString(",") { it.header })
                w.write("\r\n")
                val select = file.columns.joinToString(", ") { it.sqlColumn }
                jdbc.query(
                    "SELECT $select FROM ${file.table} WHERE revision_id = ? ORDER BY id",
                    RowCallbackHandler { rs ->
                        val line =
                            file.columns
                                .mapIndexed { i, col ->
                                    // ResultSet.getObject returns java.sql.Date for DATE columns, but
                                    // formatCell expects java.time.LocalDate — convert here.
                                    val raw = rs.getObject(i + 1)
                                    val v = if (raw is Date) raw.toLocalDate() else raw
                                    csv(formatCell(v, col.kind))
                                }.joinToString(",")
                        w.write(line)
                        w.write("\r\n")
                    },
                    revisionId,
                )
                w.flush()
                zip.closeEntry()
            }
        }
    }

    private fun rowCount(
        table: String,
        revisionId: Long,
    ): Long = jdbc.queryForObject("SELECT count(*) FROM $table WHERE revision_id = ?", Long::class.java, revisionId) ?: 0L

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
