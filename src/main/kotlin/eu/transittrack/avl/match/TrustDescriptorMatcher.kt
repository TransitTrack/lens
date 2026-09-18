package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.Point
import eu.transittrack.avl.AvlProperties
import eu.transittrack.schedule.model.TripRef

/**
 * Matcher for feeds whose vehicle descriptors are trusted. The trip is resolved from `descTripId`
 * when present, otherwise from `descRouteId` (+ `descDirectionId`, `descStartDate`, `descStartTimeSec`)
 * with a spatial disambiguation when the descriptor still leaves several trips in play. Matching is
 * then reduced to a spatial projection plus schedule adherence, with a sequential (no-backward-jump)
 * constraint against the vehicle's previous state and the descriptor's `currentStopId` /
 * `currentStopSequence`, and a single block-advance hop when a vehicle has run off the end of its
 * current trip.
 */
@Component
class TrustDescriptorMatcher(
    private val spatial: SpatialMatcher,
    private val temporal: TemporalMatcher,
    props: AvlProperties,
) : VehicleMatcher {
    private val cfg = props.match
    override val mode = AvlAssignmentMode.TRUST_DESCRIPTOR

    override fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome {
        val point = Point(report.lat, report.lon)

        // 1. Resolve the trip: descTripId first, else route + descriptor fields.
        val (trip, geom) = resolveTrip(report, ctx, point) ?: return MatchOutcome.Failed

        // 2. service date + actual service seconds
        val serviceDate = inferServiceDate(report, trip, ctx.zone)
        val actualServiceSec = temporal.toServiceSeconds(report.ts, ctx.zone, serviceDate)

        // 3. spatial match with the sequential constraint: prev on the SAME trip, and the descriptor's
        //    current stop, whichever is further along.
        val prevAlong = prev?.takeIf { it.tripRowId == trip.id }?.distanceAlongTripM
        val stopHintAlong = stopHintAlongM(report, geom, trip.tripPatternId, ctx)
        var activeTrip = trip
        var activeGeom = geom
        var sm = spatial.match(activeGeom, point, maxOfNullable(prevAlong, stopHintAlong))

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
                activeTrip = advanced.first
                activeGeom = advanced.second
                sm = spatial.match(activeGeom, point, null)
            }
        }
        val match = sm ?: return MatchOutcome.Failed

        // 5. temporal
        val adherence =
            temporal.adherenceSec(
                ctx.scheduleOf(activeTrip.id),
                activeGeom.stopPathCumM,
                match.distanceAlongTripM,
                actualServiceSec,
                activeTrip.noSchedule == true,
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
        ctx: MatchContext,
        point: Point,
    ): Pair<TripRef, PatternGeometry>? {
        report.descTripId?.let { id ->
            ctx.tripByGtfsId(id)?.let { return geometryOf(it, ctx) }
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
                else -> disambiguateSpatially(exact, report, ctx, point)
            }
        }

        return disambiguateSpatially(candidates, report, ctx, point)
    }

    /**
     * Keep only trips whose pattern carries the reported current stop and onto which the report
     * actually projects; resolve to a single trip or `null` when still ambiguous.
     */
    private fun disambiguateSpatially(
        candidates: List<TripRef>,
        report: AvlReportView,
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
                val minAlong = stopHintAlongM(report, geom, patternId, ctx)
                if (spatial.match(geom, point, minAlong) == null) null else t to geom
            }
        return survivors.singleOrNull()
    }

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
