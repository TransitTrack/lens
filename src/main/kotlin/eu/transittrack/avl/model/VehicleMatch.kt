package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** Ordinal 0 = PENDING must match the `vehicle_match.prediction_status` column default. */
enum class PredictionStatus { PENDING, DONE, FAILED }

@Entity
@Table(name = "vehicle_match")
class VehicleMatch(
    @Column(name = "avl_report_id", nullable = false) var avlReportId: Long,
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(nullable = false) var ts: Instant,
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "block_pk") var blockPk: Long?,
    @Column(name = "trip_pattern_id") var tripPatternId: Long?,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "distance_along_trip_m", nullable = false) var distanceAlongTripM: Double,
    @Column(name = "deviation_m", nullable = false) var deviationM: Double,
    @Column(name = "schedule_adherence_sec") var scheduleAdherenceSec: Int?,
    @Column(name = "snapped_lat", nullable = false) var snappedLat: Double,
    @Column(name = "snapped_lon", nullable = false) var snappedLon: Double,
    @Column var heading: Double?,
    @Column var score: Double?,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Enumerated
    @Column(name = "prediction_status", nullable = false)
    var predictionStatus: PredictionStatus = PredictionStatus.PENDING,
    @Column(name = "predicted_at") var predictedAt: Instant? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

interface PendingPredictionMetrics {
    val feedId: Long
    val pendingCount: Long
    val oldestCreatedAt: Instant?
}

@Repository
interface VehicleMatchRepository : JpaRepository<VehicleMatch, Long> {
    fun findByFeedIdAndVehicleIdOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        pageable: Pageable,
    ): List<VehicleMatch>

    fun findByFeedIdAndVehicleIdAndTsGreaterThanEqualOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        ts: Instant,
        pageable: Pageable,
    ): List<VehicleMatch>

    /** The vehicle's most recent match strictly before [ts] — used as the crossing-detection "prev"
     * when a claimed batch's oldest row for this vehicle isn't the vehicle's very first match. Relies
     * on [findClaimBatch] claiming PENDING rows oldest-first per vehicle: any earlier row for the same
     * vehicle was necessarily already processed in a prior batch, so this is never itself PENDING. */
    fun findTopByFeedIdAndVehicleIdAndTsLessThanOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        ts: Instant,
    ): VehicleMatch?

    @Query(
        "select t from VehicleMatch t where t.predictionStatus = 0 and t.feedId = :feedId " +
            "order by t.vehicleId, t.ts limit :limit",
    )
    fun findClaimBatch(
        @Param("feedId") feedId: Long,
        @Param("limit") limit: Int,
    ): List<VehicleMatch>

    @Modifying
    @Transactional
    @Query("update VehicleMatch set predictionStatus = :status, predictedAt = :at where id in :ids")
    fun markPredicted(
        ids: List<Long>,
        status: PredictionStatus,
        at: Instant,
    )

    @Query(
        "select r.feedId as feedId, count(r) as pendingCount, min(r.createdAt) as oldestCreatedAt " +
            "from VehicleMatch r where r.predictionStatus = 0 group by r.feedId",
    )
    fun pendingPredictionMetricsByFeed(): List<PendingPredictionMetrics>
}
