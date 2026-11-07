package eu.transittrack.avl.match

import kotlin.math.abs

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties
import eu.transittrack.geo.Point
import eu.transittrack.schedule.model.TripRef
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Matcher for feeds whose vehicle descriptors are trusted. The trip is resolved from `descTripId`
 * when present, otherwise from `descRouteId` (+ `descDirectionId`, `descStartDate`, `descStartTimeSec`)
 * with a spatial disambiguation when the descriptor still leaves several trips in play, and then a
 * preference for the vehicle's previous trip, its block's next trip, or the trip nearest its
 * schedule, in that order. Matching is
 * then reduced to a spatial projection plus schedule adherence, with a sequential (no-backward-jump)
 * constraint against the vehicle's previous state and the descriptor's `currentStopId` /
 * `currentStopSequence`, and a single block-advance hop when a vehicle has run off the end of its
 * current trip.
 */
@Component
class TrustDescriptorMatcher(
    private val spatial: SpatialMatcher,
    private val metrics: TransitTrackMetrics,
    props: AvlProperties,
) : VehicleMatcher {
    private val cfg = props.match
    private val log = LoggerFactory.getLogger(javaClass)
    override val mode = AvlAssignmentMode.TRUST_DESCRIPTOR

    override fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome {
        // 1. Resolve the trip: descTripId first, else route + descriptor fields.
        val (trip, geom) = resolveTrip(report, prev, ctx, report.point) ?: return MatchOutcome.Failed

        // 2. service date + actual service seconds
        val serviceDate = inferServiceDate(report, trip, ctx.zone)
        val actualServiceSec = TemporalMatcher.toServiceSeconds(report.ts, ctx.zone, serviceDate)

        // 3. spatial match with the sequential constraint: prev on the SAME trip, and the descriptor's
        //    current stop, whichever is further along.
        val prevAlong = prev?.takeIf { it.tripRowId == trip.id }?.distanceAlongTripM
        val stopHintAlong = stopHintAlongM(report, geom, trip.tripPatternId, ctx)
        var activeTrip = trip
        var activeGeom = geom
        var sm = spatial.match(activeGeom, report.point, maxOfNullable(prevAlong, stopHintAlong))

        // 4. block advance: prev is at/near the end of this trip AND report ts is past the trip end
        if (sm == null && prevAlong != null) {
            val advanced =
                tryBlockAdvance(
                    prev,
                    activeGeom,
                    activeTrip,
                    actualServiceSec,
                    ctx,
                    cfg.backtrackToleranceM,
                    cfg.tripEndAdvanceGraceSec,
                )
            if (advanced != null) {
                log.warn(
                    "avl feed '{}' vehicle '{}': block-advance overrides descriptor trip '{}' with '{}' " +
                        "(descTripId={}, prevAlongM={}, tripLenM={})",
                    report.feedCode,
                    report.vehicleId,
                    activeTrip.tripId,
                    advanced.first.tripId,
                    report.descTripId,
                    prevAlong,
                    activeGeom.line.lengthM,
                )
                metrics.avlBlockAdvance(report.feedCode)
                activeTrip = advanced.first
                activeGeom = advanced.second
                sm = spatial.match(activeGeom, report.point, null)
            }
        }
        val match = sm ?: return MatchOutcome.Failed

        // 5. temporal
        val adherence =
            TemporalMatcher.adherenceSec(
                ctx.scheduleOf(activeTrip.id),
                activeGeom.stopPathCumM,
                match.distanceAlongTripM,
                actualServiceSec,
                activeTrip.noSchedule == true,
                activeGeom.line.lengthM,
            )
        val blockPk = ctx.blockTripOf(activeTrip.id)?.blockId
        return MatchOutcome.Matched(
            tripRowId = activeTrip.id,
            blockPk = blockPk,
            tripPatternId = activeTrip.tripPatternId!!,
            stopPathIndex = match.stopPathIndex,
            distanceAlongTripM = match.distanceAlongTripM,
            deviationM = match.deviationM,
            scheduleAdherenceSec = adherence,
            snapped = match.snapped,
            heading = match.heading,
            score = null,
            revisionId = ctx.revisionId,
        )
    }

    private fun resolveTrip(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
        point: Point,
    ): Pair<TripRef, PatternGeometry>? {
        report.descTripId?.let { id ->
            ctx.tripByGtfsId(id)?.let { return geometryOf(it, ctx) }
            log.warn(
                "avl feed '{}': descTripId '{}' not found in active revision, falling back to route/time resolution",
                report.feedCode,
                id,
            )
            metrics.avlDescTripIdFallback(report.feedCode)
        }

        val routeId = report.descRouteId ?: return null
        val serviceDate = report.descStartDate ?: report.ts.atZone(ctx.zone).toLocalDate()
        val serviceIds = ctx.activeServiceIds(serviceDate)
        if (serviceIds.isEmpty()) return null

        var candidates = ctx.candidateTripsForRoute(routeId, serviceIds)
        report.descDirectionId?.let { d -> candidates = candidates.filter { it.directionId == d } }
        if (candidates.isEmpty()) return null

        // A present start time is authoritative: exactly one trip must carry it, otherwise the
        // descriptor is inconsistent with this revision and we defer to inference.
        report.descStartTimeSec?.let { start ->
            val exact = candidates.filter { it.startTimeSec == start }
            return when {
                exact.size == 1 -> geometryOf(exact.single(), ctx)
                exact.isEmpty() -> null
                else -> disambiguate(exact, report, prev, ctx, point)
            }
        }

        return disambiguate(candidates, report, prev, ctx, point)
    }

    private data class Survivor(
        val trip: TripRef,
        val geom: PatternGeometry,
        val match: SpatialMatch,
    )

    /**
     * Keep only trips whose pattern carries the reported current stop and onto which the report
     * actually projects (no further back than the vehicle's previous position on that same trip).
     * When several remain (a route's trips in one direction all share its shape), prefer the
     * vehicle's previous trip, then the next trip of its block, then the trip nearest its schedule;
     * `null` only when no trip survives, or none of the survivors has a usable schedule.
     */
    private fun disambiguate(
        candidates: List<TripRef>,
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
        point: Point,
    ): Pair<TripRef, PatternGeometry>? {
        val survivors =
            candidates.mapNotNull { t ->
                val patternId = t.tripPatternId ?: return@mapNotNull null
                if (report.currentStopId != null &&
                    ctx.patternStops(patternId).none { it.stopId == report.currentStopId }
                ) {
                    return@mapNotNull null
                }
                val geom = ctx.patternGeometry(patternId) ?: return@mapNotNull null
                val prevAlong = prev?.takeIf { it.tripRowId == t.id }?.distanceAlongTripM
                val minAlong = maxOfNullable(prevAlong, stopHintAlongM(report, geom, patternId, ctx))
                spatial.match(geom, point, minAlong)?.let { Survivor(t, geom, it) }
            }
        val chosen =
            survivors.singleOrNull()
                ?: preferPrevious(survivors, prev, ctx)
                ?: nearestToSchedule(survivors, report, ctx)
        return chosen?.let { it.trip to it.geom }
    }

    /** The vehicle's previous trip among [survivors], else the next trip of its block. */
    private fun preferPrevious(
        survivors: List<Survivor>,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): Survivor? {
        val prevTripId = prev?.tripRowId ?: return null
        survivors.firstOrNull { it.trip.id == prevTripId }?.let { return it }
        val bt = ctx.blockTripOf(prevTripId) ?: return null
        val next = ctx.nextBlockTrip(bt.blockId, bt.listIndex) ?: return null
        return survivors.firstOrNull { it.trip.id == next.tripId }
    }

    /** The survivor whose schedule the report is closest to; trips without a usable schedule are skipped. */
    private fun nearestToSchedule(
        survivors: List<Survivor>,
        report: AvlReportView,
        ctx: MatchContext,
    ): Survivor? =
        survivors
            .mapNotNull { s ->
                val serviceSec = TemporalMatcher.toServiceSeconds(report.ts, ctx.zone, inferServiceDate(report, s.trip, ctx.zone))
                TemporalMatcher
                    .adherenceSec(
                        ctx.scheduleOf(s.trip.id),
                        s.geom.stopPathCumM,
                        s.match.distanceAlongTripM,
                        serviceSec,
                        s.trip.noSchedule == true,
                        s.geom.line.lengthM,
                    )?.let { s to abs(it) }
            }.minByOrNull { it.second }
            ?.first

    private fun geometryOf(
        trip: TripRef,
        ctx: MatchContext,
    ): Pair<TripRef, PatternGeometry>? {
        val geom = trip.tripPatternId?.let { ctx.patternGeometry(it) } ?: return null
        return trip to geom
    }

    /** Cumulative distance to the descriptor's `currentStopId` / `currentStopSequence`, when it resolves. */
    private fun stopHintAlongM(
        report: AvlReportView,
        geom: PatternGeometry,
        tripPatternId: Long?,
        ctx: MatchContext,
    ): Double? {
        if (report.currentStopId == null && report.currentStopSequence == null) return null
        val patternId = tripPatternId ?: return null
        val stops = ctx.patternStops(patternId)
        val sp =
            stops.firstOrNull { report.currentStopId != null && it.stopId == report.currentStopId }
                ?: stops.firstOrNull { report.currentStopSequence != null && it.stopSeq == report.currentStopSequence }
                ?: return null
        return geom.stopPathCumM.getOrNull(sp.stopPathIndex)
    }

    private fun maxOfNullable(
        a: Double?,
        b: Double?,
    ): Double? =
        when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
}
