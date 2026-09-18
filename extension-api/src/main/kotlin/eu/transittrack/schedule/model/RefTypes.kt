package eu.transittrack.schedule.model

/** Lean, JPA-free mirror of `eu.transittrack.gtfs.model.Trip`, for extension-api consumers. */
data class TripRef(
    val id: Long,
    val revisionId: Long,
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String?,
    val tripShortName: String?,
    val directionId: Int?,
    val blockId: String?,
    val shapeId: String?,
    val wheelchairAccessible: Int?,
    val bikesAllowed: Int?,
    val tripPatternId: Long?,
    val startTimeSec: Int?,
    val endTimeSec: Int?,
    val frequencyBased: Boolean?,
    val noSchedule: Boolean?,
)

/** Lean, JPA-free mirror of `eu.transittrack.schedule.model.BlockTrip`. */
data class BlockTripRef(
    val id: Long,
    val revisionId: Long,
    val blockId: Long,
    val tripId: Long,
    val listIndex: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
)

/** Lean, JPA-free mirror of `eu.transittrack.schedule.model.StopPath`. */
data class StopPathRef(
    val id: Long,
    val revisionId: Long,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val stopId: String,
    val routeId: String?,
    val stopSeq: Int,
    val lengthM: Double,
    val pathGeometry: String?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val waitStop: Boolean,
    val scheduleAdherenceStop: Boolean,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
)
