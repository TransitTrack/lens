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

/** Windowed mean stop-to-stop time for one stop path of a pattern, in one day-type/time-band bucket. */
@Entity
@Table(name = "travel_time_observation")
class TravelTimeObservation(
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(name = "pattern_key", nullable = false) var patternKey: String,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(name = "day_type", nullable = false) var dayType: DayType,
    @Enumerated @Column(name = "time_band", nullable = false) var timeBand: TimeBand,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "mean_sec", nullable = false) var meanSec: Double,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "travelTimeObservationSeq")
    @SequenceGenerator(name = "travelTimeObservationSeq", sequenceName = "travel_time_observation_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface TravelTimeObservationRepository : JpaRepository<TravelTimeObservation, Long> {
    @Query(
        "select e from TravelTimeObservation e where e.gtfsFeedCode = :gtfsFeedCode and e.patternKey = :patternKey " +
            "and e.stopPathIndex = :stopPathIndex and e.dayType = :dayType and e.timeBand = :timeBand",
    )
    fun findInBucket(
        @Param("gtfsFeedCode") gtfsFeedCode: String,
        @Param("patternKey") patternKey: String,
        @Param("stopPathIndex") stopPathIndex: Int,
        @Param("dayType") dayType: DayType,
        @Param("timeBand") timeBand: TimeBand,
    ): TravelTimeObservation?

    @Query("select e from TravelTimeObservation e where e.gtfsFeedCode = :gtfsFeedCode and e.patternKey = :patternKey")
    fun findByPattern(
        @Param("gtfsFeedCode") gtfsFeedCode: String,
        @Param("patternKey") patternKey: String,
    ): List<TravelTimeObservation>
}
