package eu.transittrack.gtfs.read

import eu.transittrack.gtfs.api.dto.AgencyDto
import eu.transittrack.gtfs.api.dto.CalendarDateDto
import eu.transittrack.gtfs.api.dto.CalendarDto
import eu.transittrack.gtfs.api.dto.FeedInfoDto
import eu.transittrack.gtfs.api.dto.FrequencyDto
import eu.transittrack.gtfs.api.dto.LevelDto
import eu.transittrack.gtfs.api.dto.PathwayDto
import eu.transittrack.gtfs.api.dto.RouteDto
import eu.transittrack.gtfs.api.dto.ShapeDto
import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.api.dto.StopTimeDto
import eu.transittrack.gtfs.api.dto.TransferDto
import eu.transittrack.gtfs.api.dto.TripDto
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FeedInfoRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.LevelRepository
import eu.transittrack.gtfs.model.PathwayRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TransferRepository
import eu.transittrack.gtfs.model.TripRepository
import org.springframework.stereotype.Service

/**
 * Read-side facade for a revision's core GTFS entities: one method per GraphQL
 * query. Each resolves `(feedCode, revisionId?)` to a concrete revision id via
 * [RevisionResolver], hits the matching repository and maps rows to DTOs (which
 * carry `revisionId` + `feedCode` for downstream field resolvers).
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
) {
    fun agencies(feedCode: String, revisionId: String?): List<AgencyDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return agencies.findByRevisionId(rev).map { AgencyDto.of(it, rev, feedCode) }
    }

    fun routes(feedCode: String, revisionId: String?): List<RouteDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return routes.findByRevisionId(rev).map { RouteDto.of(it, rev, feedCode) }
    }

    fun route(feedCode: String, routeId: String, revisionId: String?): RouteDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return routes.findByRouteId(rev, routeId)?.let { RouteDto.of(it, rev, feedCode) }
    }

    fun stops(feedCode: String, revisionId: String?): List<StopDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return stops.findByRevisionId(rev).map { StopDto.of(it, rev, feedCode) }
    }

    fun stop(feedCode: String, stopId: String, revisionId: String?): StopDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return stops.findByStopId(rev, stopId)?.let { StopDto.of(it, rev, feedCode) }
    }

    fun trips(feedCode: String, routeId: String?, serviceId: String?, revisionId: String?): List<TripDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = when {
            routeId != null -> trips.findByRouteId(rev, routeId)
            serviceId != null -> trips.findByServiceId(rev, serviceId)
            else -> trips.findByRevisionId(rev)
        }
        return list.map { TripDto.of(it, rev, feedCode) }
    }

    fun trip(feedCode: String, tripId: String, revisionId: String?): TripDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return trips.findByTripId(rev, tripId)?.let { TripDto.of(it, rev, feedCode) }
    }

    fun stopTimes(feedCode: String, tripId: String, revisionId: String?): List<StopTimeDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return stopTimes.findByTripId(rev, tripId)
            .map { StopTimeDto.of(it, rev, feedCode) }
    }

    fun calendars(feedCode: String, revisionId: String?): List<CalendarDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return calendars.findByRevisionId(rev).map { CalendarDto.of(it, rev, feedCode) }
    }

    fun calendarDates(feedCode: String, serviceId: String?, revisionId: String?): List<CalendarDateDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = if (serviceId != null) calendarDates.findByServiceId(rev, serviceId)
        else calendarDates.findByRevisionId(rev)
        return list.map { CalendarDateDto.of(it, rev, feedCode) }
    }

    fun shape(feedCode: String, shapeId: String, revisionId: String?): ShapeDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        val shape = shapes.findByShapeId(rev, shapeId) ?: return null
        val pts = shapePoints.findByShapeId(rev, shapeId)
        return ShapeDto.of(shape, pts, rev, feedCode)
    }

    fun frequencies(feedCode: String, tripId: String?, revisionId: String?): List<FrequencyDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = if (tripId != null) frequencies.findByTripId(rev, tripId)
        else frequencies.findByRevisionId(rev)
        return list.map { FrequencyDto.of(it, rev, feedCode) }
    }

    fun transfers(feedCode: String, revisionId: String?): List<TransferDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return transfers.findByRevisionId(rev).map { TransferDto.of(it, rev, feedCode) }
    }

    fun feedInfo(feedCode: String, revisionId: String?): FeedInfoDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return feedInfos.findByRevisionId(rev)?.let { FeedInfoDto.of(it, rev, feedCode) }
    }

    fun pathways(feedCode: String, revisionId: String?): List<PathwayDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return pathways.findByRevisionId(rev).map { PathwayDto.of(it, rev, feedCode) }
    }

    fun levels(feedCode: String, revisionId: String?): List<LevelDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return levels.findByRevisionId(rev).map { LevelDto.of(it, rev, feedCode) }
    }
}
