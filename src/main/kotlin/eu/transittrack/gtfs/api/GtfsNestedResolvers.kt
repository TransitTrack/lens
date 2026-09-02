package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.AgencyDto
import eu.transittrack.gtfs.api.dto.LevelDto
import eu.transittrack.gtfs.api.dto.RouteDto
import eu.transittrack.gtfs.api.dto.ShapeDto
import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.api.dto.StopTimeDto
import eu.transittrack.gtfs.api.dto.TripDto
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.LevelRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository

/**
 * Nested-field (`@SchemaMapping`) resolvers that walk the GTFS graph without new query arguments.
 * Each parent DTO carries `revisionId` + `feedCode` (set by
 * [eu.transittrack.gtfs.read.GtfsReadService]); every resolver scopes its repository lookup by that
 * `revisionId` and threads `(revisionId, feedCode)` into the child DTO so the chain can continue
 * arbitrarily deep against a single revision.
 */
@Controller
class GtfsNestedResolvers(
    private val agencies: AgencyRepository,
    private val routes: RouteRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val stops: StopRepository,
    private val shapes: ShapeRepository,
    private val shapePoints: ShapePointRepository,
    private val levels: LevelRepository,
) {
    @SchemaMapping(typeName = "GtfsRoute")
    fun agency(r: RouteDto): AgencyDto? =
        r.agencyId
            ?.let { agencies.findByAgencyId(r.revisionId, it) }
            ?.let { AgencyDto.of(it, r.revisionId, r.feedCode) }

    @SchemaMapping(typeName = "GtfsRoute")
    fun trips(r: RouteDto): List<TripDto> =
        trips
            .findByRouteId(r.revisionId, r.routeId)
            .map {
                TripDto.of(it, r.revisionId, r.feedCode)
            }

    @SchemaMapping(typeName = "GtfsTrip")
    fun route(t: TripDto): RouteDto? =
        routes
            .findByRouteId(t.revisionId, t.routeId)
            ?.let {
                RouteDto.of(it, t.revisionId, t.feedCode)
            }

    @SchemaMapping(typeName = "GtfsTrip")
    fun stopTimes(t: TripDto): List<StopTimeDto> =
        stopTimes
            .findByTripId(t.revisionId, t.tripId)
            .map {
                StopTimeDto.of(it, t.revisionId, t.feedCode)
            }

    @SchemaMapping(typeName = "GtfsTrip")
    fun shape(t: TripDto): ShapeDto? {
        val sid = t.shapeId ?: return null
        val shape = shapes.findByShapeId(t.revisionId, sid) ?: return null
        val pts = shapePoints.findByShapeId(t.revisionId, sid)
        return ShapeDto.of(shape, pts, t.revisionId, t.feedCode)
    }

    @SchemaMapping(typeName = "GtfsStopTime")
    fun stop(st: StopTimeDto): StopDto? =
        st.stopId
            ?.let {
                stops.findByStopId(st.revisionId, it)
            }?.let { StopDto.of(it, st.revisionId, st.feedCode) }

    @SchemaMapping(typeName = "GtfsStop")
    fun childStops(s: StopDto): List<StopDto> =
        stops.findByParentStation(s.revisionId, s.stopId).map {
            StopDto.of(it, s.revisionId, s.feedCode)
        }

    @SchemaMapping(typeName = "GtfsStop")
    fun level(s: StopDto): LevelDto? =
        s.levelId
            ?.let {
                levels.findByLevelId(s.revisionId, it)
            }?.let { LevelDto.of(it, s.revisionId, s.feedCode) }
}
