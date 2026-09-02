package eu.transittrack.gtfs.feed

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)
