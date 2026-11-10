package eu.transittrack.schedule.model

import eu.transittrack.gtfs.model.RevisionScoped

/** One segment of a trip pattern, ending at `stopId`. Persisted by `StopPathTable` in core. */
class StopPath(
    revisionId: Long,
    /** Assigned once the owning pattern is inserted (see `TripPatternProcessor`). */
    var tripPatternId: Long,
    val stopPathIndex: Int,
    val stopId: String,
    val routeId: String? = null,
    val stopSeq: Int,
    val lengthM: Double,
    val pickupType: Int?,
    val dropOffType: Int?,
    val waitStop: Boolean,
    val scheduleAdherenceStop: Boolean,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
) : RevisionScoped(revisionId)
