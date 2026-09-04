package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface VehicleMatchRepository : JpaRepository<VehicleMatch, Long> {
    fun findByFeedIdAndVehicleIdOrderByTsDesc(
        feedId: Long,
        vehicleId: String,
        pageable: Pageable,
    ): List<VehicleMatch>
}
