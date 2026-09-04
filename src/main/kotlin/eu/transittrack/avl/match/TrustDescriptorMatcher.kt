package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.Point
import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleStateRow

/**
 * Matcher for feeds whose vehicle descriptors are trusted: the trip is taken straight from
 * `descTripId`, and matching is reduced to a spatial projection plus schedule adherence, with a
 * sequential (no-backward-jump) constraint against the vehicle's previous state and a single
 * block-advance hop when a vehicle has run off the end of its current trip.
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
        report: AvlReportRow,
        prev: VehicleStateRow?,
        ctx: AvlMatchContext,
    ): MatchOutcome {
        // 1. Resolve the trip strictly from descTripId.
        // TODO(avl): resolve descriptor by route+direction+startTime when descTripId is absent
        val trip = report.descTripId?.let { ctx.tripByGtfsId(it) } ?: return MatchOutcome.Failed
        val patternId = trip.tripPatternId ?: return MatchOutcome.Failed
        val geom = ctx.patternGeometry(patternId) ?: return MatchOutcome.Failed

        // 2. service date + actual service seconds
        val serviceDate = inferServiceDate(report, trip, ctx.zone)
        val actualServiceSec = temporal.toServiceSeconds(report.ts, ctx.zone, serviceDate)

        // 3. spatial match with the sequential constraint against prev on the SAME trip
        val point = Point(report.lat, report.lon)
        val minAlong = prev?.takeIf { it.tripRowId == trip.id }?.distanceAlongTripM
        var activeTrip = trip
        var activeGeom = geom
        var sm = spatial.match(activeGeom, point, minAlong)

        // 4. block advance: prev is at/near the end of this trip AND report ts is past the trip end
        if (sm == null && minAlong != null) {
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
                ctx.scheduleOf(activeTrip.id!!),
                activeGeom.stopPathCumM,
                match.distanceAlongTripM,
                actualServiceSec,
                activeTrip.noSchedule == true,
            )
        val blockPk = ctx.blockTripOf(activeTrip.id!!)?.blockId
        return MatchOutcome.Matched(
            tripRowId = activeTrip.id!!,
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
}
