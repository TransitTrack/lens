package eu.transittrack.schedule.model

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

import eu.transittrack.Extent
import eu.transittrack.gtfs.model.RevisionScoped

/**
 * Derived transit model (TransitClock-style) for one GTFS revision. Every row carries the
 * `revision_id` it was derived under; tables cascade-delete with `gtfs_revision`. Clock fields are
 * seconds into the service day.
 */
@Entity
@Table(name = "trip_patterns")
class TripPattern(
    revisionId: Long,
    @Column(name = "pattern_key", nullable = false) var patternKey: String,
    @Column(name = "route_id", nullable = false) var routeId: String,
    var routeShortName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var directionId: Int?,
    @Column(length = 500) var headsign: String?,
    var shapeId: String?,
    @Column(name = "stop_count", nullable = false) var stopCount: Int,
    @Column(name = "length_m") var lengthM: Double?,
    @Embedded var extent: Extent = Extent(),
    @Column(name = "trip_count", nullable = false) var tripCount: Int,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "stop_path")
class StopPath(
    revisionId: Long,
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "stop_id", nullable = false) var stopId: String,
    @Column(name = "route_id") var routeId: String? = null,
    @Column(name = "gtfs_stop_seq", nullable = false) var gtfsStopSeq: Int,
    @Column(name = "length_m", nullable = false) var lengthM: Double,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "path_geometry") var pathGeometry: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var pickupType: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var dropOffType: Int?,
    @Column(name = "wait_stop", nullable = false) var waitStop: Boolean,
    @Column(name = "schedule_adherence_stop", nullable = false) var scheduleAdherenceStop: Boolean,
    @Column(name = "layover_stop", nullable = false) var layoverStop: Boolean,
    var breakTimeSec: Int?,
    var typicalTravelTimeSec: Int?,
    var typicalDwellTimeSec: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "sched_trip")
class SchedTrip(
    revisionId: Long,
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    @Column(name = "route_id", nullable = false) var routeId: String,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @JdbcTypeCode(SqlTypes.SMALLINT) var directionId: Int?,
    @Column(length = 500) var headsign: String?,
    var tripShortName: String?,
    @Column(name = "start_time_sec", nullable = false) var startTimeSec: Int,
    @Column(name = "end_time_sec", nullable = false) var endTimeSec: Int,
    @Column(name = "frequency_based", nullable = false) var frequencyBased: Boolean,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exactTimes: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "schedule_time")
class ScheduleTime(
    revisionId: Long,
    @Column(name = "sched_trip_id", nullable = false) var schedTripId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    var arrivalSec: Int?,
    var departureSec: Int?,
    @Column(nullable = false) var interpolated: Boolean,
    var schedTravelTimeSec: Int?,
    var schedDwellTimeSec: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "block")
class Block(
    revisionId: Long,
    @Column(name = "block_id", nullable = false) var blockId: String,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @Column(name = "start_time_sec", nullable = false) var startTimeSec: Int,
    @Column(name = "end_time_sec", nullable = false) var endTimeSec: Int,
    @Column(name = "trip_count", nullable = false) var tripCount: Int,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "route_ids", nullable = false) var routeIds: List<String>,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "block_trip")
class BlockTrip(
    revisionId: Long,
    @Column(name = "block_id", nullable = false) var blockId: Long,
    @Column(name = "sched_trip_id", nullable = false) var schedTripId: Long,
    @Column(name = "list_index", nullable = false) var listIndex: Int,
    var layoverAfterSec: Int?,
    var deadheadAfter: Boolean?,
) : RevisionScoped(revisionId)
