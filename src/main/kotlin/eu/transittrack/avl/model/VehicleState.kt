package eu.transittrack.avl.model

import java.time.Instant
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
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(name = "vehicle_state")
class VehicleState(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface VehicleStateRepository : JpaRepository<VehicleState, Long> {
    fun findByFeedIdAndVehicleId(
        feedId: Long,
        vehicleId: String,
    ): VehicleState?

    fun findByFeedIdOrderByUpdatedAtDesc(feedId: Long): List<VehicleState>

    fun findByFeedId(feedId: Long): List<VehicleState>

    /**
     * Drops the trip/block assignment and flips `matched -> false` for vehicles silent past
     * `greatest(feed.pollIntervalSec * cycles, minSec)` seconds as of [asOf]. Used by
     * [eu.transittrack.avl.match.AvlSilentVehicleSweeper]. HQL bulk update — the per-row
     * threshold is looked up per vehicle via a correlated subquery on [AvlFeed] since
     * [VehicleState.feedId] isn't a mapped association.
     */
    @Modifying
    @Transactional
    @Query(
        """
        update VehicleState vs set
          vs.matched = false, vs.stale = true,
          vs.revisionId = null, vs.tripRowId = null, vs.blockPk = null, vs.tripPatternId = null,
          vs.stopPathIndex = null, vs.distanceAlongTripM = null, vs.scheduleAdherenceSec = null,
          vs.snappedLat = null, vs.snappedLon = null
        where vs.tripRowId is not null
          and timestampdiff(second, vs.updatedAt, :asOf) >
              greatest((select f.pollIntervalSec from AvlFeed f where f.id = vs.feedId) * :cycles, :minSec)
        """,
    )
    fun unmatchSilent(
        cycles: Int,
        minSec: Int,
        asOf: Instant,
    ): Int

    /** Flags still-matched vehicles `stale = true` once they've been silent past the same threshold. */
    @Modifying
    @Transactional
    @Query(
        """
        update VehicleState vs set vs.stale = true
        where vs.matched = true and vs.stale = false
          and timestampdiff(second, vs.updatedAt, :asOf) >
              greatest((select f.pollIntervalSec from AvlFeed f where f.id = vs.feedId) * :cycles, :minSec)
        """,
    )
    fun staleSilent(
        cycles: Int,
        minSec: Int,
        asOf: Instant,
    ): Int
}
