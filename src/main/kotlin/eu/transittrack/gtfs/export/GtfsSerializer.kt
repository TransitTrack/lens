package eu.transittrack.gtfs.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.store.GtfsTables

/**
 * Streams a revision's RAW GTFS tables to a `.zip` — one CSV per [GtfsFileSchema.FILES] entry
 * that has at least one row. Rows are consumed one at a time from the lazy Exposed [org.jetbrains.exposed.v1.jdbc.Query]
 * iterator, never collected into a list.
 */
@Component
@ExposedBridge
class GtfsSerializer {
    fun serialize(
        revisionId: Long,
        out: OutputStream,
    ) {
        ZipOutputStream(out).use { zip ->
            for (file in GtfsFileSchema.FILES) {
                val table = GtfsTables.ALL.first { it.tableName == file.table }
                if (rowCount(table, revisionId) == 0L) continue
                zip.putNextEntry(ZipEntry(file.name))
                val w = OutputStreamWriter(zip, Charsets.UTF_8)
                w.write(file.columns.joinToString(",") { it.header })
                w.write("\r\n")
                val columns: List<Column<*>> = file.columns.map { c -> table.columns.first { it.name == c.sqlColumn } }
                for (row in table.select(columns).where { table.revisionId eq revisionId }.orderBy(table.id to SortOrder.ASC)) {
                    val line =
                        file.columns
                            .mapIndexed { i, col ->
                                // formatCell compares BOOL_INT cells against Int; smallint columns come back as Short.
                                val raw = row[columns[i]]
                                csv(formatCell(if (raw is Short) raw.toInt() else raw, col.kind))
                            }.joinToString(",")
                    w.write(line)
                    w.write("\r\n")
                }
                w.flush()
                zip.closeEntry()
            }
        }
    }

    private fun rowCount(
        table: RevisionScopedTable<*>,
        revisionId: Long,
    ): Long = table.selectAll().where { table.revisionId eq revisionId }.count()

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
