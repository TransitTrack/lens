package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.DraftGridCellDto
import eu.transittrack.gtfs.api.dto.DraftGridDto
import eu.transittrack.gtfs.api.dto.DraftGridStopDto
import eu.transittrack.gtfs.api.dto.DraftGridTripDto
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository

/**
 * Read-only projection of a draft's raw `trips` + `stop_times` for one route (optionally narrowed
 * by direction / service), shaped as a timetable grid for the schedule editor frontend. No editor
 * lock and no version check — this never mutates.
 */
@Controller
class DraftGridController(
    private val drafts: DraftService,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val stops: StopRepository,
) {
    @QueryMapping
    fun draftGrid(
        @Argument draftId: String,
        @Argument routeId: String,
        @Argument directionId: Int?,
        @Argument serviceId: String?,
    ): DraftGridDto {
        val revisionId = drafts.get(draftId.toLong()).id!!

        val matchedTrips =
            trips
                .findByRouteId(revisionId, routeId)
                .filter { directionId == null || it.directionId == directionId }
                .filter { serviceId == null || it.serviceId == serviceId }

        val stopTimesByTrip: Map<String, List<StopTime>> =
            matchedTrips.associate { it.tripId to stopTimes.findByTripId(revisionId, it.tripId) }

        val tripDtos =
            matchedTrips.map { trip ->
                val sts = stopTimesByTrip[trip.tripId].orEmpty()
                DraftGridTripDto(
                    tripId = trip.tripId,
                    firstDepartureSec = sts.mapNotNull { it.departureTime }.minOrNull(),
                    headsign = trip.tripHeadsign,
                    blockId = trip.blockId,
                    directionId = trip.directionId,
                    serviceId = trip.serviceId,
                    cells =
                        sts.map { st ->
                            DraftGridCellDto(
                                stopSequence = st.stopSequence,
                                arrivalSec = st.arrivalTime,
                                departureSec = st.departureTime,
                            )
                        },
                )
            }

        // Grid columns read left-to-right chronologically.
        val orderedTrips = tripDtos.sortedWith(compareBy(nullsLast()) { it.firstDepartureSec })

        // Stop rows key on stopSequence alone, assuming one dominant stop pattern per
        // (route, direction, service); divergent-pattern stops at the same sequence collapse
        // to the first seen.
        val allStopTimes = stopTimesByTrip.values.flatten()
        val stopNameCache = mutableMapOf<String, String?>()
        val stopDtos =
            allStopTimes
                .groupBy { it.stopSequence }
                .toSortedMap()
                .map { (seq, group) ->
                    val stopId = group.firstNotNullOfOrNull { it.stopId }
                    DraftGridStopDto(
                        stopSequence = seq,
                        stopId = stopId,
                        stopName =
                            stopId?.let { id ->
                                stopNameCache.getOrPut(id) { stops.findByStopId(revisionId, id)?.stopName }
                            },
                        timepoint = group.firstNotNullOfOrNull { it.timepoint },
                    )
                }

        return DraftGridDto(stops = stopDtos, trips = orderedTrips)
    }
}
