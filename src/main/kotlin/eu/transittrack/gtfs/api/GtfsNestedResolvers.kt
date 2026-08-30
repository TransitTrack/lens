package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.api.dto.GtfsAgencyDto
import eu.transittrack.gtfs.api.dto.GtfsLevelDto
import eu.transittrack.gtfs.api.dto.GtfsRouteDto
import eu.transittrack.gtfs.api.dto.GtfsShapeDto
import eu.transittrack.gtfs.api.dto.GtfsStopDto
import eu.transittrack.gtfs.api.dto.GtfsStopTimeDto
import eu.transittrack.gtfs.api.dto.GtfsTripDto
import eu.transittrack.gtfs.model.GtfsAgencyRepository
import eu.transittrack.gtfs.model.GtfsLevelRepository
import eu.transittrack.gtfs.model.GtfsRouteRepository
import eu.transittrack.gtfs.model.GtfsShapePointRepository
import eu.transittrack.gtfs.model.GtfsShapeRepository
import eu.transittrack.gtfs.model.GtfsStopRepository
import eu.transittrack.gtfs.model.GtfsStopTimeRepository
import eu.transittrack.gtfs.model.GtfsTripRepository
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

/**
 * Nested-field (`@SchemaMapping`) resolvers that walk the GTFS graph without new
 * query arguments. Each parent DTO carries `revisionId` + `feedCode` (set by
 * [eu.transittrack.gtfs.read.GtfsReadService]); every resolver scopes its repository
 * lookup by that `revisionId` and threads `(revisionId, feedCode)` into the child DTO
 * so the chain can continue arbitrarily deep against a single revision.
 */
@Controller
class GtfsNestedResolvers(
    private val agencies: GtfsAgencyRepository,
    private val routes: GtfsRouteRepository,
    private val trips: GtfsTripRepository,
    private val stopTimes: GtfsStopTimeRepository,
    private val stops: GtfsStopRepository,
    private val shapes: GtfsShapeRepository,
    private val shapePoints: GtfsShapePointRepository,
    private val levels: GtfsLevelRepository,
) {

    @SchemaMapping(typeName = "GtfsRoute")
    fun agency(r: GtfsRouteDto): GtfsAgencyDto? =
        r.agencyId
            ?.let { agencies.findByRevisionIdAndAgencyId(r.revisionId, it) }
            ?.let { GtfsAgencyDto.of(it, r.revisionId, r.feedCode) }

    @SchemaMapping(typeName = "GtfsRoute")
    fun trips(r: GtfsRouteDto): List<GtfsTripDto> =
        trips.findByRevisionIdAndRouteId(r.revisionId, r.routeId)
            .map { GtfsTripDto.of(it, r.revisionId, r.feedCode) }

    @SchemaMapping(typeName = "GtfsTrip")
    fun route(t: GtfsTripDto): GtfsRouteDto? =
        routes.findByRevisionIdAndRouteId(t.revisionId, t.routeId)
            ?.let { GtfsRouteDto.of(it, t.revisionId, t.feedCode) }

    @SchemaMapping(typeName = "GtfsTrip")
    fun stopTimes(t: GtfsTripDto): List<GtfsStopTimeDto> =
        stopTimes.findByRevisionIdAndTripIdOrderByStopSequence(t.revisionId, t.tripId)
            .map { GtfsStopTimeDto.of(it, t.revisionId, t.feedCode) }

    @SchemaMapping(typeName = "GtfsTrip")
    fun shape(t: GtfsTripDto): GtfsShapeDto? {
        val sid = t.shapeId ?: return null
        val shape = shapes.findByRevisionIdAndShapeId(t.revisionId, sid) ?: return null
        val pts = shapePoints.findByRevisionIdAndShapeIdOrderByShapePtSequence(t.revisionId, sid)
        return GtfsShapeDto.of(shape, pts, t.revisionId, t.feedCode)
    }

    @SchemaMapping(typeName = "GtfsStopTime")
    fun stop(st: GtfsStopTimeDto): GtfsStopDto? =
        st.stopId
            ?.let { stops.findByRevisionIdAndStopId(st.revisionId, it) }
            ?.let { GtfsStopDto.of(it, st.revisionId, st.feedCode) }

    @SchemaMapping(typeName = "GtfsStop")
    fun childStops(s: GtfsStopDto): List<GtfsStopDto> =
        stops.findByRevisionIdAndParentStation(s.revisionId, s.stopId)
            .map { GtfsStopDto.of(it, s.revisionId, s.feedCode) }

    @SchemaMapping(typeName = "GtfsStop")
    fun level(s: GtfsStopDto): GtfsLevelDto? =
        s.levelId
            ?.let { levels.findByRevisionIdAndLevelId(s.revisionId, it) }
            ?.let { GtfsLevelDto.of(it, s.revisionId, s.feedCode) }
}
