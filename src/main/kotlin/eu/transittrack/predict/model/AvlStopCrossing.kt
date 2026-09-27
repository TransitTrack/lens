package eu.transittrack.predict.model

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
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Entity
@Table(name = "avl_stop_crossing")
class AvlStopCrossing(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "observed_at", nullable = false) var observedAt: Instant,
    @Column(name = "observed_travel_time_sec", nullable = false) var observedTravelTimeSec: Double,
    @Column(name = "service_id") var serviceId: String?,
    @Column(name = "trip_start_sec") var tripStartSec: Int?,
    @Column(name = "route_id") var routeId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "direction_id") var directionId: Int?,
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "avlStopCrossingSeq")
    @SequenceGenerator(name = "avlStopCrossingSeq", sequenceName = "avl_stop_crossing_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface AvlStopCrossingRepository : JpaRepository<AvlStopCrossing, Long> {
    /**
     * Bounded projection for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): filters at the query
     * level by feed, the run's frozen revision, the requested observed-date range, and the
     * optional service/route/direction/scheduled-departure-window filters, so a run never loads
     * crossings outside its own scope into memory.
     */
    @Query(
        "select c from AvlStopCrossing c where c.feedId in :feedIds and c.revisionId = :revisionId " +
            "and c.observedAt >= :from and c.observedAt < :to " +
            "and (:serviceId is null or c.serviceId = :serviceId) " +
            "and (:routeId is null or c.routeId = :routeId) " +
            "and (:directionId is null or c.directionId = :directionId) " +
            "and (:windowFromSec is null or c.tripStartSec >= :windowFromSec) " +
            "and (:windowToSec is null or c.tripStartSec <= :windowToSec)",
    )
    fun findForAnalysis(
        feedIds: Collection<Long>,
        revisionId: Long,
        from: Instant,
        to: Instant,
        serviceId: String?,
        routeId: String?,
        directionId: Int?,
        windowFromSec: Int?,
        windowToSec: Int?,
    ): List<AvlStopCrossing>
}
