package eu.transittrack.schedule.compare

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository

@Service
class ScheduleComparisonService(
    private val revisions: GtfsRevisionRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
) {
    fun compare(
        fromRevisionId: Long,
        toRevisionId: Long,
        routeId: String? = null,
        directionId: Int? = null,
        serviceId: String? = null,
    ): ScheduleComparison {
        val from = revisions.findById(fromRevisionId).orElseThrow { IllegalArgumentException("no revision $fromRevisionId") }
        val to = revisions.findById(toRevisionId).orElseThrow { IllegalArgumentException("no revision $toRevisionId") }
        require(from.feedId == to.feedId) {
            "revisions $fromRevisionId and $toRevisionId belong to different feeds"
        }

        val fromTrips = filterTrips(trips.findByRevisionId(fromRevisionId), routeId, directionId, serviceId)
        val toTrips = filterTrips(trips.findByRevisionId(toRevisionId), routeId, directionId, serviceId)
        val tripChanges =
            compareTrips(fromRevisionId, toRevisionId, fromTrips, toTrips, from.derivationStale, to.derivationStale)

        return ScheduleComparison(
            fromRevisionId = fromRevisionId,
            toRevisionId = toRevisionId,
            fromDerivationStale = from.derivationStale,
            toDerivationStale = to.derivationStale,
            tripChanges = tripChanges,
            calendarChanges = emptyList(),
            headwaySummaries = emptyList(),
        )
    }

    private fun filterTrips(
        all: List<Trip>,
        routeId: String?,
        directionId: Int?,
        serviceId: String?,
    ): List<Trip> =
        all.filter { t ->
            (routeId == null || t.routeId == routeId) &&
                (directionId == null || t.directionId == directionId) &&
                (serviceId == null || t.serviceId == serviceId)
        }

    private fun compareTrips(
        fromRevisionId: Long,
        toRevisionId: Long,
        fromTrips: List<Trip>,
        toTrips: List<Trip>,
        fromStale: Boolean,
        toStale: Boolean,
    ): List<TripChange> {
        val fromById = fromTrips.associateBy { it.tripId }
        val toById = toTrips.associateBy { it.tripId }
        val allIds = fromById.keys + toById.keys
        val changes = mutableListOf<TripChange>()
        for (tripId in allIds) {
            val f = fromById[tripId]
            val t = toById[tripId]
            when {
                f == null && t != null -> {
                    changes += TripChange(tripId, TripChangeKind.ADDED, emptyMap(), emptyList(), null)
                }

                f != null && t == null -> {
                    changes += TripChange(tripId, TripChangeKind.REMOVED, emptyMap(), emptyList(), null)
                }

                f != null && t != null -> {
                    val fieldChanges = tripFieldChanges(f, t)
                    val stopChanges = compareStopTimes(fromRevisionId, toRevisionId, tripId)
                    if (fieldChanges.isEmpty() && stopChanges.isEmpty()) continue
                    val runTimeDelta =
                        if (!fromStale &&
                            !toStale &&
                            f.startTimeSec != null &&
                            f.endTimeSec != null &&
                            t.startTimeSec != null &&
                            t.endTimeSec != null
                        ) {
                            (t.endTimeSec!! - t.startTimeSec!!) - (f.endTimeSec!! - f.startTimeSec!!)
                        } else {
                            null
                        }
                    changes += TripChange(tripId, TripChangeKind.MODIFIED, fieldChanges, stopChanges, runTimeDelta)
                }
            }
        }
        return changes.sortedBy { it.tripId }
    }

    private fun tripFieldChanges(
        f: Trip,
        t: Trip,
    ): Map<String, Pair<String?, String?>> {
        val m = mutableMapOf<String, Pair<String?, String?>>()
        if (f.routeId != t.routeId) m["routeId"] = f.routeId to t.routeId
        if (f.serviceId != t.serviceId) m["serviceId"] = f.serviceId to t.serviceId
        if (f.directionId != t.directionId) m["directionId"] = f.directionId?.toString() to t.directionId?.toString()
        if (f.blockId != t.blockId) m["blockId"] = f.blockId to t.blockId
        if (f.shapeId != t.shapeId) m["shapeId"] = f.shapeId to t.shapeId
        if (f.tripHeadsign != t.tripHeadsign) m["headsign"] = f.tripHeadsign to t.tripHeadsign
        return m
    }

    private fun compareStopTimes(
        fromRevisionId: Long,
        toRevisionId: Long,
        tripId: String,
    ): List<StopTimeChange> {
        val fromRows = stopTimes.findByTripId(fromRevisionId, tripId).associateBy { it.stopSequence }
        val toRows = stopTimes.findByTripId(toRevisionId, tripId).associateBy { it.stopSequence }
        val allSeqs = (fromRows.keys + toRows.keys).sorted()
        val out = mutableListOf<StopTimeChange>()
        for (seq in allSeqs) {
            val f = fromRows[seq]
            val t = toRows[seq]
            when {
                f == null && t != null -> {
                    out += StopTimeChange(seq, StopChangeKind.STOP_ADDED, t.stopId, null, null)
                }

                f != null && t == null -> {
                    out += StopTimeChange(seq, StopChangeKind.STOP_REMOVED, f.stopId, null, null)
                }

                f != null && t != null -> {
                    val stopChanged = f.stopId != t.stopId
                    val arrDelta = if (f.arrivalTime != null && t.arrivalTime != null) t.arrivalTime!! - f.arrivalTime!! else null
                    val depDelta = if (f.departureTime != null && t.departureTime != null) t.departureTime!! - f.departureTime!! else null
                    val arrNullChanged = (f.arrivalTime == null) != (t.arrivalTime == null)
                    val depNullChanged = (f.departureTime == null) != (t.departureTime == null)
                    val timeChanged =
                        (arrDelta != null && arrDelta != 0) ||
                            (depDelta != null && depDelta != 0) ||
                            arrNullChanged ||
                            depNullChanged
                    if (stopChanged || timeChanged) {
                        out += StopTimeChange(seq, StopChangeKind.STOP_TIME_CHANGED, t.stopId, arrDelta, depDelta)
                    }
                }
            }
        }
        return out
    }
}
