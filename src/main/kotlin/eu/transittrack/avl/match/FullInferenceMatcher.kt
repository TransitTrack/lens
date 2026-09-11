package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.Point
import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.gtfs.model.Trip

/**
 * Matcher for feeds without trusted descriptors: every derived trip active on the inferred service
 * date whose window and pattern extent bracket the report is projected, scored by [MatchScorer], and
 * the best-scoring candidate wins — with a hysteresis margin protecting the incumbent trip.
 */
@Component
class FullInferenceMatcher(
    private val spatial: SpatialMatcher,
    private val temporal: TemporalMatcher,
    private val scorer: MatchScorer,
    props: AvlProperties,
) : VehicleMatcher {
    private val cfg = props.match
    override val mode = AvlAssignmentMode.FULL_INFERENCE

    override fun match(
        report: AvlReportRow,
        prev: VehicleState?,
        ctx: AvlMatchContext,
    ): MatchOutcome {
        val point = Point(report.lat, report.lon)

        // 1. service date: descriptor startDate wins; else report ts local date (no trip yet).
        val serviceDate = report.descStartDate ?: report.ts.atZone(ctx.zone).toLocalDate()
        val serviceIds = ctx.activeServiceIds(serviceDate)
        if (serviceIds.isEmpty()) return MatchOutcome.Failed
        val actualServiceSec = temporal.toServiceSeconds(report.ts, ctx.zone, serviceDate)

        // 2. candidate trips: derived, active service, ts within [start-slack, end+slack], extent near the point.
        val descTrip = report.descTripId?.let { ctx.tripByGtfsId(it) }
        val candidates =
            (ctx.candidateTrips(serviceIds) + listOfNotNull(descTrip))
                .distinctBy { it.id }
                .filter { t ->
                    val pid = t.tripPatternId ?: return@filter false
                    val start = t.startTimeSec ?: return@filter false
                    val end = t.endTimeSec ?: return@filter false
                    actualServiceSec in (start - cfg.candidateTimeSlackSec)..(end + cfg.candidateTimeSlackSec) &&
                        ctx.patternExtentWithin(pid, point, cfg.maxDeviationM)
                }
        if (candidates.isEmpty()) return MatchOutcome.Failed

        // 3. spatial + temporal + score each candidate
        data class Scored(
            val trip: Trip,
            val geom: PatternGeometry,
            val sm: SpatialMatch,
            val adherence: Int?,
            val score: Double,
        )

        val scored =
            candidates.mapNotNull { t ->
                val geom = ctx.patternGeometry(t.tripPatternId!!) ?: return@mapNotNull null
                val minAlong = prev?.takeIf { it.tripRowId == t.id }?.distanceAlongTripM
                val sm = spatial.match(geom, point, minAlong) ?: return@mapNotNull null
                val adherence =
                    temporal.adherenceSec(
                        ctx.scheduleOf(t.id!!),
                        geom.stopPathCumM,
                        sm.distanceAlongTripM,
                        actualServiceSec,
                        t.noSchedule == true,
                    )
                val continuity = continuityOf(t, prev, ctx)
                Scored(t, geom, sm, adherence, scorer.score(sm.deviationM, report.bearing, sm.heading, adherence, continuity))
            }
        if (scored.isEmpty()) return MatchOutcome.Failed

        // 4. pick best; re-assignment hysteresis
        val best = scored.maxBy { it.score }
        val chosen =
            if (prev?.tripRowId != null && best.trip.id != prev.tripRowId) {
                val incumbent = scored.firstOrNull { it.trip.id == prev.tripRowId }
                if (incumbent != null && best.score < incumbent.score + cfg.reassignHysteresis) incumbent else best
            } else {
                best
            }

        return toMatched(chosen.trip, chosen.geom, chosen.sm, chosen.adherence, chosen.score, ctx)
    }

    private fun continuityOf(
        t: Trip,
        prev: VehicleState?,
        ctx: AvlMatchContext,
    ): Double {
        if (prev?.tripRowId == null) return 0.0
        if (t.id == prev.tripRowId) return 1.0
        val prevBt = ctx.blockTripOf(prev.tripRowId!!) ?: return 0.0
        val next = ctx.nextBlockTrip(prevBt.blockId, prevBt.listIndex) ?: return 0.0
        return if (next.tripId == t.id) 0.5 else 0.0
    }

    private fun toMatched(
        trip: Trip,
        geom: PatternGeometry,
        sm: SpatialMatch,
        adherence: Int?,
        score: Double,
        ctx: AvlMatchContext,
    ) = MatchOutcome.Matched(
        tripRowId = trip.id!!,
        blockPk = ctx.blockTripOf(trip.id!!)?.blockId,
        tripPatternId = trip.tripPatternId!!,
        stopPathIndex = sm.stopPathIndex,
        distanceAlongTripM = sm.distanceAlongTripM,
        deviationM = sm.deviationM,
        scheduleAdherenceSec = adherence,
        snapped = sm.snapped,
        heading = sm.heading,
        score = score,
        revisionId = ctx.revisionId,
    )
}
