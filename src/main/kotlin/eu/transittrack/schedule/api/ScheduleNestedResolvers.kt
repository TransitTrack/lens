package eu.transittrack.schedule.api

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.RouteDto
import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.api.dto.TripDto
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.ScheduleReadService
import eu.transittrack.schedule.read.dto.BlockDto
import eu.transittrack.schedule.read.dto.BlockTripDto
import eu.transittrack.schedule.read.dto.SchedTripDto
import eu.transittrack.schedule.read.dto.ScheduleTimeDto
import eu.transittrack.schedule.read.dto.StopPathDto
import eu.transittrack.schedule.read.dto.TripPatternDto

/**
 * `@SchemaMapping` field resolvers for the schedule graph, plus two that hang off the GTFS types.
 * Every parent DTO carries `(revisionId, feedCode)`; each resolver scopes its repository lookup by
 * that `revisionId`.
 */
@Controller
class ScheduleNestedResolvers(
    private val patterns: TripPatternRepository,
    private val schedTrips: SchedTripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val blocks: BlockRepository,
    private val blockTrips: BlockTripRepository,
    private val routes: RouteRepository,
    private val stops: StopRepository,
    private val read: ScheduleReadService,
) {
    @SchemaMapping(typeName = "TripPattern")
    fun route(p: TripPatternDto): RouteDto? =
        routes.findByRouteId(p.revisionId, p.routeId)?.let {
            RouteDto.of(it, p.revisionId, p.feedCode)
        }

    @SchemaMapping(typeName = "TripPattern")
    fun stopPaths(p: TripPatternDto): List<StopPathDto> = read.stopPathsOf(p.revisionId, p.feedCode, p.id)

    @SchemaMapping(typeName = "TripPattern")
    fun trips(p: TripPatternDto): List<SchedTripDto> =
        schedTrips.findByTripPattern(p.revisionId, p.id).map {
            SchedTripDto.of(it, p.revisionId, p.feedCode)
        }

    @SchemaMapping(typeName = "StopPath")
    fun stop(sp: StopPathDto): StopDto? =
        stops.findByStopId(sp.revisionId, sp.stopId)?.let {
            StopDto.of(it, sp.revisionId, sp.feedCode)
        }

    @SchemaMapping(typeName = "SchedTrip")
    fun pattern(t: SchedTripDto): TripPatternDto? =
        patterns.findById(t.tripPatternId).orElse(null)?.let {
            TripPatternDto.of(it, t.revisionId, t.feedCode)
        }

    @SchemaMapping(typeName = "SchedTrip")
    fun route(t: SchedTripDto): RouteDto? =
        routes.findByRouteId(t.revisionId, t.routeId)?.let {
            RouteDto.of(it, t.revisionId, t.feedCode)
        }

    @SchemaMapping(typeName = "SchedTrip")
    fun block(t: SchedTripDto): BlockDto? {
        val bt = blockTrips.findBySchedTripId(t.revisionId, t.id) ?: return null
        return blocks.findById(bt.blockId).orElse(null)?.let {
            BlockDto.of(it, t.revisionId, t.feedCode)
        }
    }

    @SchemaMapping(typeName = "SchedTrip")
    fun scheduleTimes(t: SchedTripDto): List<ScheduleTimeDto> =
        scheduleTimes.findBySchedTripOrdered(t.revisionId, t.id).map {
            ScheduleTimeDto.of(it, t.revisionId, t.feedCode)
        }

    @SchemaMapping(typeName = "Block")
    fun trips(b: BlockDto): List<SchedTripDto> {
        val members = blockTrips.findByBlockIdOrdered(b.revisionId, b.id)
        val byId = schedTrips.findAllById(members.map { it.schedTripId }).associateBy { it.id }
        return members.mapNotNull { byId[it.schedTripId] }.map { SchedTripDto.of(it, b.revisionId, b.feedCode) }
    }

    @SchemaMapping(typeName = "Block")
    fun blockTrips(b: BlockDto): List<BlockTripDto> =
        blockTrips.findByBlockIdOrdered(b.revisionId, b.id).map {
            BlockTripDto.of(it, b.revisionId, b.feedCode)
        }

    @SchemaMapping(typeName = "BlockTrip")
    fun trip(bt: BlockTripDto): SchedTripDto? =
        schedTrips.findById(bt.schedTripId).orElse(null)?.let {
            SchedTripDto.of(it, bt.revisionId, bt.feedCode)
        }

    @SchemaMapping(typeName = "Route")
    fun tripPatterns(r: RouteDto): List<TripPatternDto> =
        patterns.findByRouteId(r.revisionId, r.routeId).map {
            TripPatternDto.of(it, r.revisionId, r.feedCode)
        }

    @SchemaMapping(typeName = "Trip")
    fun schedTrip(t: TripDto): SchedTripDto? =
        schedTrips.findByTripId(t.revisionId, t.tripId)?.let {
            SchedTripDto.of(it, t.revisionId, t.feedCode)
        }

    @SchemaMapping(typeName = "Trip")
    fun tripPattern(t: TripDto): TripPatternDto? =
        t.tripPatternId
            ?.let {
                patterns.findById(it).orElse(null)
            }?.let { TripPatternDto.of(it, t.revisionId, t.feedCode) }
}
