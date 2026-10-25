package eu.transittrack.predict.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

/** Kalman stop-to-stop estimate for one stop path of a pattern, in one day-type/time-band bucket. */
@Entity
@Table(name = "kalman_travel_time_state")
class KalmanTravelTimeState(
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(name = "pattern_key", nullable = false) var patternKey: String,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(name = "day_type", nullable = false) var dayType: DayType,
    @Enumerated @Column(name = "time_band", nullable = false) var timeBand: TimeBand,
    @Column(name = "estimate_sec", nullable = false) var estimateSec: Double,
    @Column(name = "error_variance", nullable = false) var errorVariance: Double,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "kalmanTravelTimeStateSeq")
    @SequenceGenerator(name = "kalmanTravelTimeStateSeq", sequenceName = "kalman_travel_time_state_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface KalmanTravelTimeStateRepository : JpaRepository<KalmanTravelTimeState, Long> {
    @Query(
        "select e from KalmanTravelTimeState e where e.gtfsFeedCode = :gtfsFeedCode and e.patternKey = :patternKey " +
            "and e.stopPathIndex = :stopPathIndex and e.dayType = :dayType and e.timeBand = :timeBand",
    )
    fun findInBucket(
        @Param("gtfsFeedCode") gtfsFeedCode: String,
        @Param("patternKey") patternKey: String,
        @Param("stopPathIndex") stopPathIndex: Int,
        @Param("dayType") dayType: DayType,
        @Param("timeBand") timeBand: TimeBand,
    ): KalmanTravelTimeState?

    @Query("select e from KalmanTravelTimeState e where e.gtfsFeedCode = :gtfsFeedCode and e.patternKey = :patternKey")
    fun findByPattern(
        @Param("gtfsFeedCode") gtfsFeedCode: String,
        @Param("patternKey") patternKey: String,
    ): List<KalmanTravelTimeState>
}
