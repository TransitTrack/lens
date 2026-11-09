package eu.transittrack.gtfs.read

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.Agency
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDate
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FeedInfo
import eu.transittrack.gtfs.model.FeedInfoRepository
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.Level
import eu.transittrack.gtfs.model.LevelRepository
import eu.transittrack.gtfs.model.Pathway
import eu.transittrack.gtfs.model.PathwayRepository
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.Shape
import eu.transittrack.gtfs.model.ShapePoint
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.Transfer
import eu.transittrack.gtfs.model.TransferRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Read-side facade for a revision's core GTFS entities: one method per read endpoint. Each resolves
 * `(feedCode, revisionId?)` to a concrete revision id via [RevisionResolver], hits the matching
 * repository and returns the rows; mapping to API models lives in `RestEntityMapper`.
 */
@Service
class GtfsReadService(
    private val resolver: RevisionResolver,
    private val agencies: AgencyRepository,
    private val routes: RouteRepository,
    private val stops: StopRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val calendars: CalendarRepository,
    private val calendarDates: CalendarDateRepository,
    private val shapes: ShapeRepository,
    private val shapePoints: ShapePointRepository,
    private val frequencies: FrequencyRepository,
    private val transfers: TransferRepository,
    private val feedInfos: FeedInfoRepository,
    private val pathways: PathwayRepository,
    private val levels: LevelRepository,
    private val tripPatterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
) {
    fun agencies(
        feedCode: String,
        revisionId: String?,
    ): List<Agency> {
        val rev = resolver.resolve(feedCode, revisionId)
        return agencies
            .findByRevisionId(rev)
    }

    /** Every route of the revision, or only [routeIds] when given. */
    fun routes(
        feedCode: String,
        revisionId: String?,
        routeIds: List<String>? = null,
    ): List<Route> {
        val rev = resolver.resolve(feedCode, revisionId)
        if (routeIds.isNullOrEmpty()) return routes.findByRevisionId(rev)
        require(routeIds.size <= MAX_ID_FILTER) { "At most $MAX_ID_FILTER routeId values" }
        return routes.findByRouteIds(rev, routeIds.distinct())
    }

    fun route(
        feedCode: String,
        routeId: String,
        revisionId: String?,
    ): Route? {
        val rev = resolver.resolve(feedCode, revisionId)
        return routes
            .findByRouteId(rev, routeId)
    }

    /**
     * The stops [routeId] serves: every stop on its trip patterns (patterns by key, then path order),
     * each once. `null` when the route doesn't exist in the revision.
     */
    fun routeStops(
        feedCode: String,
        routeId: String,
        revisionId: String?,
    ): List<Stop>? {
        val rev = resolver.resolve(feedCode, revisionId)
        routes.findByRouteId(rev, routeId) ?: return null
        val patternIds = tripPatterns.findByRouteId(rev, routeId).sortedBy { it.patternKey }.map { it.id!! }
        val stopIds = stopPaths.findStopIdsAlongPatterns(rev, patternIds)
        if (stopIds.isEmpty()) return emptyList()
        val byId = stops.findByStopIds(rev, stopIds).associateBy { it.stopId }
        return stopIds.mapNotNull(byId::get)
    }

    /** Every stop of the revision, narrowed to a station's children and/or to [stopIds] when given. */
    fun stops(
        feedCode: String,
        revisionId: String?,
        parentStation: String? = null,
        stopIds: List<String>? = null,
    ): List<Stop> {
        val rev = resolver.resolve(feedCode, revisionId)
        if (stopIds.isNullOrEmpty()) {
            return if (parentStation != null) stops.findByParentStation(rev, parentStation) else stops.findByRevisionId(rev)
        }
        require(stopIds.size <= MAX_ID_FILTER) { "At most $MAX_ID_FILTER stopId values" }
        val found = stops.findByStopIds(rev, stopIds.distinct())
        return if (parentStation != null) found.filter { it.parentStation == parentStation } else found
    }

    fun stop(
        feedCode: String,
        stopId: String,
        revisionId: String?,
    ): Stop? {
        val rev = resolver.resolve(feedCode, revisionId)
        return stops
            .findByStopId(rev, stopId)
    }

    fun trips(
        feedCode: String,
        routeId: String?,
        serviceId: String?,
        revisionId: String?,
    ): List<Trip> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = when {
            routeId != null -> trips.findByRouteId(rev, routeId)
            serviceId != null -> trips.findByServiceId(rev, serviceId)
            else -> trips.findByRevisionId(rev)
        }
        return list
    }

    fun trip(
        feedCode: String,
        tripId: String,
        revisionId: String?,
    ): Trip? {
        val rev = resolver.resolve(feedCode, revisionId)
        return trips.findByTripId(rev, tripId)
    }

    fun tripStops(
        feedCode: String,
        tripId: String,
        revisionId: String?,
    ): List<Stop>? {
        val rev = resolver.resolve(feedCode, revisionId)
        val trip = trips.findByTripId(rev, tripId) ?: return null
        val patternIds = tripPatterns
            .findByRouteId(rev, trip.routeId)
            .sortedBy { it.patternKey }
            .map { it.id!! }
        val stopIds = stopPaths.findStopIdsAlongPatterns(rev, patternIds)
        if (stopIds.isEmpty()) return emptyList()
        val byId = stops.findByStopIds(rev, stopIds).associateBy { it.stopId }
        return stopIds.mapNotNull(byId::get)
    }

    fun stopTimes(
        feedCode: String,
        tripId: String,
        revisionId: String?,
    ): List<StopTime> {
        val rev = resolver.resolve(feedCode, revisionId)
        return stopTimes.findByTripId(rev, tripId)
    }

    fun calendars(
        feedCode: String,
        revisionId: String?,
    ): List<Calendar> {
        val rev = resolver.resolve(feedCode, revisionId)
        return calendars.findByRevisionId(rev)
    }

    fun calendarDates(
        feedCode: String,
        serviceId: String?,
        revisionId: String?,
    ): List<CalendarDate> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list =
            if (serviceId != null) {
                calendarDates.findByServiceId(rev, serviceId)
            } else {
                calendarDates.findByRevisionId(rev)
            }
        return list
    }

    fun shape(
        feedCode: String,
        shapeId: String,
        revisionId: String?,
    ): ShapeWithPoints? {
        val rev = resolver.resolve(feedCode, revisionId)
        val shape = shapes.findByShapeId(rev, shapeId) ?: return null
        val pts = shapePoints.findByShapeId(rev, shapeId)
        return ShapeWithPoints(shape, pts)
    }

    fun frequencies(
        feedCode: String,
        tripId: String?,
        revisionId: String?,
    ): List<Frequency> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list =
            if (tripId != null) {
                frequencies.findByTripId(rev, tripId)
            } else {
                frequencies.findByRevisionId(rev)
            }
        return list
    }

    fun transfers(
        feedCode: String,
        revisionId: String?,
    ): List<Transfer> {
        val rev = resolver.resolve(feedCode, revisionId)
        return transfers.findByRevisionId(rev)
    }

    fun feedInfo(
        feedCode: String,
        revisionId: String?,
    ): FeedInfo? {
        val rev = resolver.resolve(feedCode, revisionId)
        return feedInfos
            .findByRevisionId(rev)
            .firstOrNull()
    }

    fun pathways(
        feedCode: String,
        revisionId: String?,
    ): List<Pathway> {
        val rev = resolver.resolve(feedCode, revisionId)
        return pathways.findByRevisionId(rev)
    }

    fun levels(
        feedCode: String,
        revisionId: String?,
    ): List<Level> {
        val rev = resolver.resolve(feedCode, revisionId)
        return levels.findByRevisionId(rev)
    }
}

/** A shape row with its points. */
data class ShapeWithPoints(
    val shape: Shape,
    val points: List<ShapePoint>,
)

/** Upper bound on a `stopId` / `routeId` list filter, matching the page-size cap. */
private const val MAX_ID_FILTER = 500
