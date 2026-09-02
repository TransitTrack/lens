package eu.transittrack.gtfs.revision

import java.time.Instant
import java.time.LocalDate
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "gtfs_revision")
class GtfsRevision(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: GtfsRevisionStatus,
    @Column(name = "source_url", nullable = false) var sourceUrl: String,
    @Column(name = "content_sha256", length = 64) var contentSha256: String? = null,
    @Column(name = "byte_size") var byteSize: Long? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "files_present") var filesPresent: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "row_counts") var rowCounts: Map<String, Long> = emptyMap(),
    @Column(name = "feed_start_date") var feedStartDate: LocalDate? = null,
    @Column(name = "feed_end_date") var feedEndDate: LocalDate? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_report") var validationReport: String? = null,
    @Column(name = "error_message") var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "activated_at") var activatedAt: Instant? = null,
    @Column(name = "superseded_at") var supersededAt: Instant? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)
