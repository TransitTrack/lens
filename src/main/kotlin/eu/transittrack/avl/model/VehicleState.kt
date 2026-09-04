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
import org.springframework.stereotype.Repository

@Entity
@Table(name = "vehicle_state")
class VehicleStateRow(
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
interface VehicleStateRepository : JpaRepository<VehicleStateRow, Long> {
    fun findByFeedIdAndVehicleId(
        feedId: Long,
        vehicleId: String,
    ): VehicleStateRow?

    fun findByFeedIdOrderByUpdatedAtDesc(feedId: Long): List<VehicleStateRow>
}
