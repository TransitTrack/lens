package eu.transittrack.gtfs.model

/** A GTFS `trips.txt` row, plus the derived pattern/clock columns. Persisted by `TripTable` in core. */
class Trip(
    revisionId: Long,
    val routeId: String,
    val serviceId: String,
    val tripId: String,
    val tripHeadsign: String?,
    val tripShortName: String?,
    val directionId: Int?,
    val blockId: String?,
    val shapeId: String?,
    val wheelchairAccessible: Int?,
    val bikesAllowed: Int?,
    val tripPatternId: Long? = null,
    val startTimeSec: Int? = null,
    val endTimeSec: Int? = null,
    val frequencyBased: Boolean? = null,
    val noSchedule: Boolean? = null,
) : RevisionScoped(revisionId)
