package eu.transittrack.schedule.api

import eu.transittrack.gtfs.api.dto.GtfsRouteDto
import eu.transittrack.gtfs.api.dto.GtfsStopDto
import eu.transittrack.gtfs.api.dto.GtfsTripDto
import eu.transittrack.gtfs.model.GtfsRouteRepository
import eu.transittrack.gtfs.model.GtfsStopRepository
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.ScheduleReadService
import eu.transittrack.schedule.read.dto.BlockDto
import eu.transittrack.schedule.read.dto.SchedTripDto
import eu.transittrack.schedule.read.dto.ScheduleTimeDto
import eu.transittrack.schedule.read.dto.StopPathDto
import eu.transittrack.schedule.read.dto.TripPatternDto
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

/**
 * `@SchemaMapping` field resolvers for the schedule graph, plus two that hang off
 * the GTFS types. Every parent DTO carries `(revisionId, feedCode)`; each resolver
 * scopes its repository lookup by that `revisionId`.
 */
@Controller
class ScheduleNestedResolvers(
    private val patterns: TripPatternRepository,
    private val schedTrips: SchedTripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val blocks: BlockRepository,
    private val routes: GtfsRouteRepository,
    private val stops: GtfsStopRepository,
    private val read: ScheduleReadService,
) {
    @SchemaMapping(typeName = "TripPattern")
    fun route(p: TripPatternDto): GtfsRouteDto? =
        routes.findByRevisionIdAndRouteId(p.revisionId, p.routeId)
            ?.let { GtfsRouteDto.of(it, p.revisionId, p.feedCode) }

    @SchemaMapping(typeName = "TripPattern")
    fun stopPaths(p: TripPatternDto): List<StopPathDto> =
        read.stopPathsOf(p.revisionId, p.feedCode, p.id)

    @SchemaMapping(typeName = "TripPattern")
    fun trips(p: TripPatternDto): List<SchedTripDto> =
        schedTrips.findByRevisionIdAndTripPatternId(p.revisionId, p.id)
            .map { SchedTripDto.of(it, p.revisionId, p.feedCode) }

    @SchemaMapping(typeName = "StopPath")
    fun stop(sp: StopPathDto): GtfsStopDto? =
        stops.findByRevisionIdAndStopId(sp.revisionId, sp.stopId)
            ?.let { GtfsStopDto.of(it, sp.revisionId, sp.feedCode) }

    @SchemaMapping(typeName = "SchedTrip")
    fun pattern(t: SchedTripDto): TripPatternDto? =
        patterns.findById(t.tripPatternId).orElse(null)
            ?.let { TripPatternDto.of(it, t.revisionId, t.feedCode) }

    @SchemaMapping(typeName = "SchedTrip")
    fun route(t: SchedTripDto): GtfsRouteDto? =
        routes.findByRevisionIdAndRouteId(t.revisionId, t.routeId)
            ?.let { GtfsRouteDto.of(it, t.revisionId, t.feedCode) }

    @SchemaMapping(typeName = "SchedTrip")
    fun block(t: SchedTripDto): BlockDto? {
        val bid = t.blockId ?: return null
        return blocks.findByRevisionIdAndBlockIdAndServiceId(t.revisionId, bid, t.serviceId)
            ?.let { BlockDto.of(it, t.revisionId, t.feedCode) }
    }

    @SchemaMapping(typeName = "SchedTrip")
    fun scheduleTimes(t: SchedTripDto): List<ScheduleTimeDto> =
        scheduleTimes.findByRevisionIdAndSchedTripIdOrderByStopPathIndex(t.revisionId, t.id)
            .map { ScheduleTimeDto.of(it, t.revisionId, t.feedCode) }

    @SchemaMapping(typeName = "Block")
    fun trips(b: BlockDto): List<SchedTripDto> =
        schedTrips.findByRevisionIdAndBlockIdOrderByBlockSeq(b.revisionId, b.blockId)
            .filter { it.serviceId == b.serviceId }
            .map { SchedTripDto.of(it, b.revisionId, b.feedCode) }

    @SchemaMapping(typeName = "GtfsRoute")
    fun tripPatterns(r: GtfsRouteDto): List<TripPatternDto> =
        patterns.findByRevisionIdAndRouteId(r.revisionId, r.routeId)
            .map { TripPatternDto.of(it, r.revisionId, r.feedCode) }

    @SchemaMapping(typeName = "GtfsTrip")
    fun schedTrip(t: GtfsTripDto): SchedTripDto? =
        schedTrips.findByRevisionIdAndTripId(t.revisionId, t.tripId)
            ?.let { SchedTripDto.of(it, t.revisionId, t.feedCode) }
}
