package eu.transittrack.gtfs.read

import eu.transittrack.gtfs.api.dto.GtfsAgencyDto
import eu.transittrack.gtfs.api.dto.GtfsCalendarDateDto
import eu.transittrack.gtfs.api.dto.GtfsCalendarDto
import eu.transittrack.gtfs.api.dto.GtfsFeedInfoDto
import eu.transittrack.gtfs.api.dto.GtfsFrequencyDto
import eu.transittrack.gtfs.api.dto.GtfsLevelDto
import eu.transittrack.gtfs.api.dto.GtfsPathwayDto
import eu.transittrack.gtfs.api.dto.GtfsRouteDto
import eu.transittrack.gtfs.api.dto.GtfsShapeDto
import eu.transittrack.gtfs.api.dto.GtfsStopDto
import eu.transittrack.gtfs.api.dto.GtfsStopTimeDto
import eu.transittrack.gtfs.api.dto.GtfsTransferDto
import eu.transittrack.gtfs.api.dto.GtfsTripDto
import eu.transittrack.gtfs.model.GtfsAgencyRepository
import eu.transittrack.gtfs.model.GtfsCalendarDateRepository
import eu.transittrack.gtfs.model.GtfsCalendarRepository
import eu.transittrack.gtfs.model.GtfsFeedInfoRepository
import eu.transittrack.gtfs.model.GtfsFrequencyRepository
import eu.transittrack.gtfs.model.GtfsLevelRepository
import eu.transittrack.gtfs.model.GtfsPathwayRepository
import eu.transittrack.gtfs.model.GtfsRouteRepository
import eu.transittrack.gtfs.model.GtfsShapePointRepository
import eu.transittrack.gtfs.model.GtfsShapeRepository
import eu.transittrack.gtfs.model.GtfsStopRepository
import eu.transittrack.gtfs.model.GtfsStopTimeRepository
import eu.transittrack.gtfs.model.GtfsTransferRepository
import eu.transittrack.gtfs.model.GtfsTripRepository
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
    private val agencies: GtfsAgencyRepository,
    private val routes: GtfsRouteRepository,
    private val stops: GtfsStopRepository,
    private val trips: GtfsTripRepository,
    private val stopTimes: GtfsStopTimeRepository,
    private val calendars: GtfsCalendarRepository,
    private val calendarDates: GtfsCalendarDateRepository,
    private val shapes: GtfsShapeRepository,
    private val shapePoints: GtfsShapePointRepository,
    private val frequencies: GtfsFrequencyRepository,
    private val transfers: GtfsTransferRepository,
    private val feedInfos: GtfsFeedInfoRepository,
    private val pathways: GtfsPathwayRepository,
    private val levels: GtfsLevelRepository,
) {
    fun agencies(feedCode: String, revisionId: String?): List<GtfsAgencyDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return agencies.findByRevisionId(rev).map { GtfsAgencyDto.of(it, rev, feedCode) }
    }

    fun routes(feedCode: String, revisionId: String?): List<GtfsRouteDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return routes.findByRevisionId(rev).map { GtfsRouteDto.of(it, rev, feedCode) }
    }

    fun route(feedCode: String, routeId: String, revisionId: String?): GtfsRouteDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return routes.findByRevisionIdAndRouteId(rev, routeId)?.let { GtfsRouteDto.of(it, rev, feedCode) }
    }

    fun stops(feedCode: String, revisionId: String?): List<GtfsStopDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return stops.findByRevisionId(rev).map { GtfsStopDto.of(it, rev, feedCode) }
    }

    fun stop(feedCode: String, stopId: String, revisionId: String?): GtfsStopDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return stops.findByRevisionIdAndStopId(rev, stopId)?.let { GtfsStopDto.of(it, rev, feedCode) }
    }

    fun trips(feedCode: String, routeId: String?, serviceId: String?, revisionId: String?): List<GtfsTripDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = when {
            routeId != null -> trips.findByRevisionIdAndRouteId(rev, routeId)
            serviceId != null -> trips.findByRevisionIdAndServiceId(rev, serviceId)
            else -> trips.findByRevisionId(rev)
        }
        return list.map { GtfsTripDto.of(it, rev, feedCode) }
    }

    fun trip(feedCode: String, tripId: String, revisionId: String?): GtfsTripDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return trips.findByRevisionIdAndTripId(rev, tripId)?.let { GtfsTripDto.of(it, rev, feedCode) }
    }

    fun stopTimes(feedCode: String, tripId: String, revisionId: String?): List<GtfsStopTimeDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return stopTimes.findByRevisionIdAndTripIdOrderByStopSequence(rev, tripId)
            .map { GtfsStopTimeDto.of(it, rev, feedCode) }
    }

    fun calendars(feedCode: String, revisionId: String?): List<GtfsCalendarDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return calendars.findByRevisionId(rev).map { GtfsCalendarDto.of(it, rev, feedCode) }
    }

    fun calendarDates(feedCode: String, serviceId: String?, revisionId: String?): List<GtfsCalendarDateDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = if (serviceId != null) calendarDates.findByRevisionIdAndServiceId(rev, serviceId)
        else calendarDates.findByRevisionId(rev)
        return list.map { GtfsCalendarDateDto.of(it, rev, feedCode) }
    }

    fun shape(feedCode: String, shapeId: String, revisionId: String?): GtfsShapeDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        val shape = shapes.findByRevisionIdAndShapeId(rev, shapeId) ?: return null
        val pts = shapePoints.findByRevisionIdAndShapeIdOrderByShapePtSequence(rev, shapeId)
        return GtfsShapeDto.of(shape, pts, rev, feedCode)
    }

    fun frequencies(feedCode: String, tripId: String?, revisionId: String?): List<GtfsFrequencyDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list = if (tripId != null) frequencies.findByRevisionIdAndTripId(rev, tripId)
        else frequencies.findByRevisionId(rev)
        return list.map { GtfsFrequencyDto.of(it, rev, feedCode) }
    }

    fun transfers(feedCode: String, revisionId: String?): List<GtfsTransferDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return transfers.findByRevisionId(rev).map { GtfsTransferDto.of(it, rev, feedCode) }
    }

    fun feedInfo(feedCode: String, revisionId: String?): GtfsFeedInfoDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return feedInfos.findByRevisionId(rev)?.let { GtfsFeedInfoDto.of(it, rev, feedCode) }
    }

    fun pathways(feedCode: String, revisionId: String?): List<GtfsPathwayDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return pathways.findByRevisionId(rev).map { GtfsPathwayDto.of(it, rev, feedCode) }
    }

    fun levels(feedCode: String, revisionId: String?): List<GtfsLevelDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return levels.findByRevisionId(rev).map { GtfsLevelDto.of(it, rev, feedCode) }
    }
}
