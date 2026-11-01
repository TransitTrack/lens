package eu.transittrack.gtfs.feed

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

import eu.transittrack.db.RowTable

@Entity
@Table(name = "gtfs_feed")
class GtfsFeed(
    @Column(nullable = false, unique = true) var code: String,
    @Column(nullable = false) var name: String,
    @Column var description: String?,
    @Column(nullable = false) var url: String,
    @Column(name = "polling_cron") var pollingCron: String?,
    @Column(nullable = false) var enabled: Boolean,
    @Column(name = "auto_activate") var autoActivate: Boolean?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var source: FeedSource,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Column(name = "last_ingest_at") var lastIngestAt: Instant? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gtfsFeedSeq")
    @SequenceGenerator(name = "gtfsFeedSeq", sequenceName = "gtfs_feed_seq", allocationSize = 50)
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
