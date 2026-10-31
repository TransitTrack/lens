package eu.transittrack.gtfs.feed

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

import eu.transittrack.db.bridge.ExposedBridge

@Repository
@ExposedBridge
class GtfsFeedRepository {
    fun findByCode(code: String): GtfsFeed? =
        GtfsFeedTable
            .selectAll()
            .where { GtfsFeedTable.code eq code }
            .singleOrNull()
            ?.let(GtfsFeedTable::read)

    fun findAllEnabled(): List<GtfsFeed> =
        GtfsFeedTable
            .selectAll()
            .where { GtfsFeedTable.enabled eq true }
            .map(GtfsFeedTable::read)

    fun findById(id: Long): GtfsFeed? =
        GtfsFeedTable
            .selectAll()
            .where { GtfsFeedTable.id eq id }
            .singleOrNull()
            ?.let(GtfsFeedTable::read)

    fun findAll(): List<GtfsFeed> = GtfsFeedTable.selectAll().map(GtfsFeedTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: GtfsFeed): GtfsFeed {
        val id = row.id
        if (id == null) {
            row.id = GtfsFeedTable.insert { GtfsFeedTable.bind(it, row) }[GtfsFeedTable.id]
        } else {
            GtfsFeedTable.update({ GtfsFeedTable.id eq id }) { GtfsFeedTable.bind(it, row) }
        }
        return row
    }

    fun delete(row: GtfsFeed) {
        val id = row.id ?: return
        deleteById(id)
    }

    fun deleteById(id: Long) {
        GtfsFeedTable.deleteWhere { GtfsFeedTable.id eq id }
    }
}
