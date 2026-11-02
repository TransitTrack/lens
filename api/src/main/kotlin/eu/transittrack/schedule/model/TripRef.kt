package eu.transittrack.schedule.model

/** Lean, JPA-free mirror of `eu.transittrack.gtfs.model.Trip`, for api consumers. */
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
