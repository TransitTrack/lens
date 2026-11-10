package eu.transittrack.support

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.schedule.model.BlockTrip

/** Test-only copy of a [Trip] (row classes are immutable and not data classes); `id` is carried over unless overridden. */
fun Trip.copy(
    id: Long? = this.id,
    tripId: String = this.tripId,
    routeId: String = this.routeId,
    blockId: String? = this.blockId,
    wheelchairAccessible: Int? = this.wheelchairAccessible,
): Trip =
    Trip(
        revisionId = revisionId,
        routeId = routeId,
        serviceId = serviceId,
        tripId = tripId,
        tripHeadsign = tripHeadsign,
        tripShortName = tripShortName,
        directionId = directionId,
        blockId = blockId,
        shapeId = shapeId,
        wheelchairAccessible = wheelchairAccessible,
        bikesAllowed = bikesAllowed,
        tripPatternId = tripPatternId,
        startTimeSec = startTimeSec,
        endTimeSec = endTimeSec,
        frequencyBased = frequencyBased,
        noSchedule = noSchedule,
    ).also { it.id = id }

/** Test-only copy of a [BlockTrip]; `id` is carried over. */
fun BlockTrip.copy(deadheadAfter: Boolean? = this.deadheadAfter): BlockTrip =
    BlockTrip(revisionId, blockId, tripId, listIndex, layoverAfterSec, deadheadAfter).also { it.id = id }
