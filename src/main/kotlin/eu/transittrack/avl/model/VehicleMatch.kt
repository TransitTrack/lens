package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
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
    @Column(name = "feed_code", nullable = false) var feedCode: String,
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
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehicleMatchSeq")
    @SequenceGenerator(name = "vehicleMatchSeq", sequenceName = "vehicle_match_seq", allocationSize = 50)
    var id: Long? = null,
)

interface PendingPredictionMetrics {
    val feedCode: String
    val pendingCount: Long
    val oldestCreatedAt: Instant?
}

@Repository
interface VehicleMatchRepository : JpaRepository<VehicleMatch, Long> {
    fun findByAvlReportIdIn(avlReportIds: Collection<Long>): List<VehicleMatch>

    @Query("select e from VehicleMatch e where e.feedCode = :feedCode and e.vehicleId = :vehicleId order by e.ts desc")
    fun findByVehicle(
        @Param("feedCode") feedCode: String,
        @Param("vehicleId") vehicleId: String,
        pageable: Pageable,
    ): List<VehicleMatch>

    @Query(
        "select e from VehicleMatch e where e.feedCode = :feedCode and e.vehicleId = :vehicleId " +
            "and e.ts >= :ts order by e.ts desc",
    )
    fun findByVehicleSince(
        @Param("feedCode") feedCode: String,
        @Param("vehicleId") vehicleId: String,
        @Param("ts") ts: Instant,
        pageable: Pageable,
    ): List<VehicleMatch>

    /** The vehicle's most recent match strictly before [ts] — used as the crossing-detection "prev"
     * when a claimed batch's oldest row for this vehicle isn't the vehicle's very first match. Relies
     * on [findClaimBatch] claiming PENDING rows oldest-first per vehicle: any earlier row for the same
     * vehicle was necessarily already processed in a prior batch, so this is never itself PENDING. */
    @Query(
        "select e from VehicleMatch e where e.feedCode = :feedCode and e.vehicleId = :vehicleId " +
            "and e.ts < :ts order by e.ts desc limit 1",
    )
    fun findPreviousForVehicle(
        @Param("feedCode") feedCode: String,
        @Param("vehicleId") vehicleId: String,
        @Param("ts") ts: Instant,
    ): VehicleMatch?

    // Ordered by ts first (global FIFO), vehicleId only as a tiebreaker: ordering by vehicleId first
    // sorts vehicle IDs as strings (e.g. "1002" < "547" lexicographically), so once a backlog exceeds
    // `limit` rows, the same lexicographically-early vehicle IDs would win every single claim and
    // starve the rest indefinitely. Sorting by ts first still preserves the oldest-first-per-vehicle
    // guarantee the comment above relies on, since any older row for the same vehicle necessarily
    // sorts before a newer one.
    @Query(
        "select t from VehicleMatch t where t.predictionStatus = 0 and t.feedCode = :feedCode " +
            "order by t.ts, t.vehicleId limit :limit",
    )
    fun findClaimBatch(
        @Param("feedCode") feedCode: String,
        @Param("limit") limit: Int,
    ): List<VehicleMatch>

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update VehicleMatch set predictionStatus = :status, predictedAt = :at where id in :ids")
    fun markPredicted(
        ids: List<Long>,
        status: PredictionStatus,
        at: Instant,
    )

    @Query(
        "select r.feedCode as feedCode, count(r) as pendingCount, min(r.createdAt) as oldestCreatedAt " +
            "from VehicleMatch r where r.predictionStatus = 0 group by r.feedCode",
    )
    fun pendingPredictionMetricsByFeed(): List<PendingPredictionMetrics>

    @Query(
        "select e from VehicleMatch e where e.feedCode = :feedCode and e.ts >= :from and e.ts < :until " +
            "order by e.vehicleId, e.ts",
    )
    fun findInWindow(
        @Param("feedCode") feedCode: String,
        @Param("from") from: Instant,
        @Param("until") until: Instant,
    ): List<VehicleMatch>

    @Query(
        "select e from VehicleMatch e where e.feedCode = :feedCode and e.tripRowId in :tripRowIds " +
            "and e.ts >= :from and e.ts < :until order by e.vehicleId, e.ts",
    )
    fun findForTripsInWindow(
        @Param("feedCode") feedCode: String,
        @Param("tripRowIds") tripRowIds: Collection<Long>,
        @Param("from") from: Instant,
        @Param("until") until: Instant,
    ): List<VehicleMatch>
}
