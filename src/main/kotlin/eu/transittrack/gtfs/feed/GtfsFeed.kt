package eu.transittrack.gtfs.feed

import java.time.Instant

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

import eu.transittrack.db.RowTable

class GtfsFeed(
    var code: String,
    var name: String,
    var description: String?,
    var url: String,
    var pollingCron: String?,
    var enabled: Boolean,
    var autoActivate: Boolean?,
    var source: FeedSource,
    var createdAt: Instant,
    var updatedAt: Instant,
    var lastIngestAt: Instant? = null,
    var id: Long? = null,
)

object GtfsFeedTable : RowTable<GtfsFeed>("gtfs_feed") {
    val id = long("id").autoIncrement(Sequence("gtfs_feed_seq", startWith = 1, incrementBy = 50))
    val code = varchar("code", 64).uniqueIndex("gtfs_feed_code_key")
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val url = text("url")
    val pollingCron = varchar("polling_cron", 64).nullable()
    val enabled = bool("enabled").default(true)
    val autoActivate = bool("auto_activate").nullable()
    val feedSource = enumerationByName<FeedSource>("source", 16)
    val lastIngestAt = timestampTz("last_ingest_at").nullable()
    val createdAt = timestampTz("created_at")
    val updatedAt = timestampTz("updated_at")

    override val primaryKey = PrimaryKey(id, name = "gtfs_feed_pkey")

    override fun read(row: ResultRow) =
        GtfsFeed(
            code = row[code],
            name = row[name],
            description = row[description],
            url = row[url],
            pollingCron = row[pollingCron],
            enabled = row[enabled],
            autoActivate = row[autoActivate],
            source = row[feedSource],
            createdAt = row[createdAt],
            updatedAt = row[updatedAt],
            lastIngestAt = row[lastIngestAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: GtfsFeed,
    ) {
        s[code] = row.code
        s[name] = row.name
        s[description] = row.description
        s[url] = row.url
        s[pollingCron] = row.pollingCron
        s[enabled] = row.enabled
        s[autoActivate] = row.autoActivate
        s[feedSource] = row.source
        s[lastIngestAt] = row.lastIngestAt
        s[createdAt] = row.createdAt
        s[updatedAt] = row.updatedAt
    }
}
