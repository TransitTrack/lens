package eu.transittrack.reports.read

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Service

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlReportView
import eu.transittrack.avl.match.TemporalMatcher
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.reports.model.StopPassage
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripRef

/**
 * Derives per-stop schedule adherence ("stop passages") from persisted AVL [VehicleMatch] history
 * - on demand, per query, with no new persisted table. For every scheduled stop of every trip
 * execution in the requested window, this reports whether the vehicle arrived on time, early,
 * late, or never reached that stop at all (`observedSec`/`adherenceSec` both `null`).
 *
 * "On time" math itself is not recomputed here: [VehicleMatch.scheduleAdherenceSec] is already
 * computed and persisted per AVL ping during live matching: this service only reuses that stored
 * value for whichever match is picked as the observed passage of a stop.
 */
@Service
class StopPassageDerivationService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val avlReports: AvlReportRowRepository,
    private val trips: TripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
    private val revisionResolver: RevisionResolver,
    private val matchContextFactory: AvlMatchContextFactory,
) {
    fun derive(
        feedCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        routeId: String? = null,
        tripId: String? = null,
    ): List<StopPassage> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val zone = matchContextFactory.open(feed)?.zone ?: ZoneId.systemDefault()
        val revisionId = revisionResolver.resolve(feed.gtfsFeedCode, null)

        val from: Instant = startDate.atStartOfDay(zone).toInstant()
        val until: Instant = endDate.plusDays(1).atStartOfDay(zone).toInstant()

        val matches: List<VehicleMatch> =
            when {
                tripId != null -> {
                    val trip =
                        trips.findByTripId(revisionId, tripId)
                            ?: throw IllegalArgumentException("no trip '$tripId' in revision $revisionId")
                    vehicleMatches.findByFeedCodeAndTripRowIdInAndTsBetween(feedCode, setOf(trip.id!!), from, until)
                }

                routeId != null -> {
                    val routeTrips = trips.findByRouteId(revisionId, routeId)
                    if (routeTrips.isEmpty()) {
                        throw IllegalArgumentException("no route '$routeId' in revision $revisionId")
                    }
                    vehicleMatches.findByFeedCodeAndTripRowIdInAndTsBetween(
                        feedCode,
                        routeTrips.map { it.id!! },
                        from,
                        until,
                    )
                }

                else -> {
                    vehicleMatches.findByFeedCodeAndTsBetween(feedCode, from, until)
                }
            }
        if (matches.isEmpty()) return emptyList()

        val reportsById =
            avlReports.findAllById(matches.map { it.avlReportId }.distinct()).associateBy { it.id }

        val tripCache = mutableMapOf<Long, Trip?>()

        fun tripFor(tripRowId: Long): Trip? = tripCache.getOrPut(tripRowId) { trips.findById(tripRowId).orElse(null) }

        data class ResolvedMatch(
            val match: VehicleMatch,
            val trip: Trip,
            val serviceDate: LocalDate,
        )

        val resolved =
            matches.mapNotNull { match ->
                val report = reportsById[match.avlReportId] ?: return@mapNotNull null
                val trip = tripFor(match.tripRowId) ?: return@mapNotNull null
                val serviceDate = inferServiceDate(report.toView(), trip.toRef(), zone)
                ResolvedMatch(match, trip, serviceDate)
            }

        val scheduleCache = mutableMapOf<Long, List<ScheduleTime>>()
        val stopPathCache = mutableMapOf<Long, List<StopPath>>()

        return resolved
            .groupBy { it.trip.id!! to it.serviceDate }
            .flatMap { (key, group) ->
                val (tripRowId, serviceDate) = key
                val trip = group.first().trip
                val tripPatternId = trip.tripPatternId ?: return@flatMap emptyList()

                val schedule = scheduleCache.getOrPut(tripRowId) { scheduleTimes.findByTripOrdered(revisionId, tripRowId) }
                val paths = stopPathCache.getOrPut(tripPatternId) { stopPaths.findByTripPatternOrdered(revisionId, tripPatternId) }
                val stopIdByIndex = paths.associate { it.stopPathIndex to it.stopId }

                val sortedMatches = group.map { it.match }.sortedBy { it.ts }

                schedule.map { scheduleTime ->
                    val observedMatch = sortedMatches.firstOrNull { it.stopPathIndex >= scheduleTime.stopPathIndex }
                    StopPassage(
                        tripId = trip.tripId,
                        routeId = trip.routeId,
                        blockId = trip.blockId,
                        serviceDate = serviceDate,
                        stopId = stopIdByIndex[scheduleTime.stopPathIndex] ?: "",
                        stopPathIndex = scheduleTime.stopPathIndex,
                        scheduledSec = scheduleTime.arrivalSec ?: scheduleTime.departureSec,
                        observedSec = observedMatch?.let { TemporalMatcher().toServiceSeconds(it.ts, zone, serviceDate) },
                        adherenceSec = observedMatch?.scheduleAdherenceSec,
                    )
                }
            }
    }
}

private fun AvlReportRow.toView() =
    AvlReportView(
        id = id!!,
        feedCode = feedCode,
        vehicleId = vehicleId,
        vehicleLabel = vehicleLabel,
        ts = ts,
        lat = lat,
        lon = lon,
        bearing = bearing,
        speedMps = speedMps,
        odometerM = odometerM,
        descTripId = descTripId,
        descRouteId = descRouteId,
        descDirectionId = descDirectionId,
        descStartDate = descStartDate,
        descStartTimeSec = descStartTimeSec,
        descScheduleRelationship = descScheduleRelationship,
        currentStopSequence = currentStopSequence,
        currentStopId = currentStopId,
        currentStatus = currentStatus,
        occupancyStatus = occupancyStatus,
        congestionLevel = congestionLevel,
    )

private fun Trip.toRef() =
    TripRef(
        id = id!!,
        revisionId = revisionId,
        tripId = tripId,
        routeId = routeId,
        serviceId = serviceId,
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
    )
