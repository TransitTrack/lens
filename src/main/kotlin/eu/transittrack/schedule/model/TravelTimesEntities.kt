package eu.transittrack.schedule.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

import eu.transittrack.gtfs.model.RevisionScoped

/** How a travel/dwell time was determined. Only schedule-derived today; AVL etc. later. */
enum class TravelTimeSource { SCHEDULE, }

/**
 * Typical (median) scheduled travel + dwell time for one stop path of a trip pattern, in seconds.
 * One row per `(revision_id, trip_pattern_id, stop_path_index)`. Replaces the former
 * `stop_path.typical_travel_time_sec` / `typical_dwell_time_sec` columns.
 */
@Entity
@Table(name = "travel_times_for_stop_path")
class TravelTimesForStopPath(
    revisionId: Long,
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "travel_time_sec") var travelTimeSec: Int?,
    @Column(name = "dwell_time_sec") var dwellTimeSec: Int?,
    @Enumerated
    @Column(name = "how_set", nullable = false)
    var howSet: TravelTimeSource = TravelTimeSource.SCHEDULE,
) : RevisionScoped(revisionId)
