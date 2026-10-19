package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(name = "vehicle_state")
class VehicleState(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "vehicle_label") var vehicleLabel: String?,
    @Column(name = "report_ts", nullable = false) var reportTs: Instant,
    @Column(nullable = false) var lat: Double,
    @Column(nullable = false) var lon: Double,
    @Column var bearing: Double?,
    @Column(name = "speed_mps") var speedMps: Double?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "occupancy_status") var occupancyStatus: Int?,
    @Column(nullable = false) var matched: Boolean,
    @Column(nullable = false) var stale: Boolean,
    @Column(name = "consecutive_failures", nullable = false) var consecutiveFailures: Int,
    @Column(name = "revision_id") var revisionId: Long?,
    @Column(name = "trip_row_id") var tripRowId: Long?,
    @Column(name = "block_pk") var blockPk: Long?,
    @Column(name = "trip_pattern_id") var tripPatternId: Long?,
    @Column(name = "stop_path_index") var stopPathIndex: Int?,
    @Column(name = "distance_along_trip_m") var distanceAlongTripM: Double?,
    @Column(name = "schedule_adherence_sec") var scheduleAdherenceSec: Int?,
    @Column(name = "snapped_lat") var snappedLat: Double?,
    @Column(name = "snapped_lon") var snappedLon: Double?,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehicleStateSeq")
    @SequenceGenerator(name = "vehicleStateSeq", sequenceName = "vehicle_state_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface VehicleStateRepository : JpaRepository<VehicleState, Long> {
    @Query("from VehicleState where feedCode = :feedCode and vehicleId = :vehicleId order by updatedAt desc")
    fun findByFeedCodeAndVehicleId(
        feedCode: String,
        vehicleId: String,
    ): VehicleState?

    @Query("from VehicleState where feedCode = :feedCode order by updatedAt desc")
    fun findByFeedCodeOrderByUpdatedAtDesc(feedCode: String): List<VehicleState>

    @Query("from VehicleState where feedCode = :feedCode")
    fun findByFeedCode(feedCode: String): List<VehicleState>

    /**
     * Drops the trip/block assignment and flips `matched -> false` for [feedCode]'s vehicles silent
     * past [thresholdSec] as of [asOf]. Used by [eu.transittrack.avl.match.AvlSilentVehicleSweeper],
     * once per feed with [thresholdSec] precomputed in application code — a correlated subquery on
     * [AvlFeed] here would otherwise be re-evaluated for every [VehicleState] row on every sweep tick.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        update VehicleState vs set
          vs.matched = false, vs.stale = true,
          vs.revisionId = null, vs.tripRowId = null, vs.blockPk = null, vs.tripPatternId = null,
          vs.stopPathIndex = null, vs.distanceAlongTripM = null, vs.scheduleAdherenceSec = null,
          vs.snappedLat = null, vs.snappedLon = null
        where vs.feedCode = :feedCode and vs.tripRowId is not null
          and timestampdiff(second,
                (select max(r.ts) from AvlReportRow r where r.feedCode = vs.feedCode and r.vehicleId = vs.vehicleId),
                :asOf) > :thresholdSec
        """,
    )
    fun unmatchSilent(
        feedCode: String,
        thresholdSec: Int,
        asOf: Instant,
    ): Int

    /** Flags [feedCode]'s still-matched vehicles `stale = true` once they've been silent past [thresholdSec]. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        update VehicleState vs set vs.stale = true
        where vs.feedCode = :feedCode and vs.matched = true and vs.stale = false
          and timestampdiff(second,
                (select max(r.ts) from AvlReportRow r where r.feedCode = vs.feedCode and r.vehicleId = vs.vehicleId),
                :asOf) > :thresholdSec
        """,
    )
    fun staleSilent(
        feedCode: String,
        thresholdSec: Int,
        asOf: Instant,
    ): Int
}
