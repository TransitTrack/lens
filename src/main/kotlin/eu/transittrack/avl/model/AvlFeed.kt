package eu.transittrack.avl.model

import java.time.Instant
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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat

enum class AvlFeedSourceKind { CONFIG, API }

@Entity
@Table(name = "avl_feed")
class AvlFeed(
    @Column(nullable = false, unique = true) var code: String,
    @Column(nullable = false) var name: String,
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(nullable = false) var url: String,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(nullable = false) var format: AvlFormat,
    @Column(name = "poll_interval_sec", nullable = false) var pollIntervalSec: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "assignment_mode", nullable = false)
    var assignmentMode: AvlAssignmentMode,
    @Column(nullable = false) var enabled: Boolean,
    @JdbcTypeCode(SqlTypes.JSON) @Column var headers: Map<String, String>?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var source: AvlFeedSourceKind,
    @Column(name = "last_poll_at") var lastPollAt: Instant? = null,
    @Column(name = "last_poll_status") var lastPollStatus: String? = null,
    @Column(name = "last_poll_report_count") var lastPollReportCount: Int? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface AvlFeedRepository : JpaRepository<AvlFeed, Long> {
    @Query("select f from AvlFeed f where f.code = :code")
    fun findByCode(code: String): AvlFeed?

    @Query("select f from AvlFeed f where f.enabled = true")
    fun findAllEnabled(): List<AvlFeed>
}
