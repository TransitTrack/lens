package eu.transittrack.avl.model

import java.time.Instant
import java.time.LocalDate
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** Ordinal 0 = PENDING must match the `avl_report.match_status` column default. */
enum class MatchStatus { PENDING, MATCHED, UNMATCHED, SKIPPED }

@Entity
@Table(name = "avl_report")
class AvlReportRow(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "vehicle_label") var vehicleLabel: String?,
    @Column(nullable = false) var ts: Instant,
    @Column(nullable = false) var lat: Double,
    @Column(nullable = false) var lon: Double,
    @Column var bearing: Double?,
    @Column(name = "speed_mps") var speedMps: Double?,
    @Column(name = "odometer_m") var odometerM: Double?,
    @Column(name = "desc_trip_id") var descTripId: String?,
    @Column(name = "desc_route_id") var descRouteId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "desc_direction_id") var descDirectionId: Int?,
    @Column(name = "desc_start_date") var descStartDate: LocalDate?,
    @Column(name = "desc_start_time_sec") var descStartTimeSec: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "desc_schedule_relationship") var descScheduleRelationship: Int?,
    @Column(name = "current_stop_sequence") var currentStopSequence: Int?,
    @Column(name = "current_stop_id") var currentStopId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "current_status") var currentStatus: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "occupancy_status") var occupancyStatus: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "congestion_level") var congestionLevel: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "match_status", nullable = false) var matchStatus: MatchStatus,
    @Column(name = "matched_at") var matchedAt: Instant?,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

interface VehicleTsProjection {
    val vehicleId: String
    val ts: Instant
}

interface PendingAvlMetrics {
    val feedId: Long
    val pendingCount: Long
    val oldestCreatedAt: Instant?
}

@Repository
interface AvlReportRowRepository : JpaRepository<AvlReportRow, Long> {
    @Query(
        "select r.feedId as feedId, count(r) as pendingCount, min(r.createdAt) as oldestCreatedAt " +
            "from AvlReportRow r where r.matchStatus = 0 group by r.feedId",
    )
    fun pendingMetricsByFeed(): List<PendingAvlMetrics>

    @Query("select t from AvlReportRow t where t.matchStatus = 0 order by t.feedId, t.vehicleId, t.ts limit :limit")
    fun findClaimBatch(
        @Param("limit") limit: Int,
    ): List<AvlReportRow>

    @Query(
        "select t from AvlReportRow t where t.matchStatus = 0 and t.feedId = :feedId " +
            "order by t.vehicleId, t.ts limit :limit",
    )
    fun findClaimBatch(
        @Param("feedId") feedId: Long,
        @Param("limit") limit: Int,
    ): List<AvlReportRow>

    @Query(
        "select r.vehicleId as vehicleId, max(r.ts) as ts from AvlReportRow r " +
            "where r.feedId = :feedId group by r.vehicleId",
    )
    fun latestTsByVehicle(feedId: Long): List<VehicleTsProjection>

    fun findByFeedIdAndVehicleIdOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        pageable: org.springframework.data.domain.Pageable,
    ): List<AvlReportRow>

    fun findByFeedIdAndVehicleIdAndTsGreaterThanEqualOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        ts: Instant,
        pageable: org.springframework.data.domain.Pageable,
    ): List<AvlReportRow>

    /**
     * Recent reports for a whole batch of vehicles in one query — feeds the movement-trend heading
     * (see [eu.transittrack.avl.match.trendHeadingDeg]) without an N+1 per vehicle.
     */
    @Query(
        "select t from AvlReportRow t where t.feedId = :feedId and t.vehicleId in :vehicleIds and t.ts >= :since " +
            "order by t.vehicleId, t.ts desc",
    )
    fun findTrail(
        @Param("feedId") feedId: Long,
        @Param("vehicleIds") vehicleIds: Collection<String>,
        @Param("since") since: Instant,
    ): List<AvlReportRow>

    @Modifying
    @Transactional
    @Query("update AvlReportRow set matchedAt = :at, matchStatus = :status where id in :ids")
    fun markMatchedBatch(
        ids: List<Long>,
        status: MatchStatus,
        at: Instant,
    )
}
