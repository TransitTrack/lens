package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.Point
import eu.transittrack.avl.AvlProperties
import eu.transittrack.schedule.model.TripRef

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
    private companion object {
        /** GPS noise can project a vehicle standing at the first stop a few metres past it. */
        const val AT_FIRST_STOP_RADIUS_M = 30.0
    }

    private val cfg = props.match
    override val mode = AvlAssignmentMode.FULL_INFERENCE

    override fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome {
        val point = Point(report.lat, report.lon)

        // 1. service date: descriptor startDate wins; else report ts local date (no trip yet).
        val serviceDate = report.descStartDate ?: report.ts.atZone(ctx.zone).toLocalDate()
        val serviceIds = ctx.activeServiceIds(serviceDate)
        if (serviceIds.isEmpty()) return MatchOutcome.Failed
        val actualServiceSec = temporal.toServiceSeconds(report.ts, ctx.zone, serviceDate)

        // 2. candidate trips: derived, active service, ts within [start-lead, end+slack], extent near the point.
        val descTrip = report.descTripId?.let { ctx.tripByGtfsId(it) }
        val candidates =
            (ctx.candidateTrips(serviceIds) + listOfNotNull(descTrip))
                .distinctBy { it.id }
                .filter { t ->
                    val pid = t.tripPatternId ?: return@filter false
                    val start = t.startTimeSec ?: return@filter false
                    val end = t.endTimeSec ?: return@filter false
                    actualServiceSec in (start - cfg.candidateLeadSec)..(end + cfg.candidateTimeSlackSec) &&
                        ctx.patternExtentWithin(pid, point, cfg.maxDeviationM)
                }
        if (candidates.isEmpty()) return MatchOutcome.Failed

        // 3. spatial + temporal + score each candidate
        data class Scored(
            val trip: TripRef,
            val geom: PatternGeometry,
            val sm: SpatialMatch,
            val adherence: Int?,
            val score: Double,
        )

        val scored =
            candidates.mapNotNull { t ->
                val geom = ctx.patternGeometry(t.tripPatternId!!) ?: return@mapNotNull null
                val minAlong = prev?.takeIf { it.tripRowId == t.id }?.distanceAlongTripM

                // spatial matching
                val sm = spatial.match(geom, point, minAlong) ?: return@mapNotNull null

                val schedule = ctx.scheduleOf(t.id)
                if (missedDeparture(t, geom, sm, schedule, actualServiceSec)) return@mapNotNull null
                if (finishedTrip(t, geom, sm, prev, actualServiceSec)) return@mapNotNull null

                // temporal matching
                val adherence =
                    temporal.adherenceSec(
                        schedule,
                        geom.stopPathCumM,
                        sm.distanceAlongTripM,
                        actualServiceSec,
                        t.noSchedule == true,
                        geom.line.lengthM,
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

    /**
     * Whether the vehicle is still at [t]'s first stop well past its scheduled departure (see
     * `match.maxFirstStopLatenessSec`). Without this, a parked vehicle stays on a trip it never
     * started: continuity and hysteresis keep the incumbent ahead of the next departure, with its
     * delay growing until the trip leaves the candidate window. Frequency-based and unscheduled
     * trips have no clock-time departure to compare against.
     */
    private fun missedDeparture(
        t: TripRef,
        geom: PatternGeometry,
        sm: SpatialMatch,
        schedule: List<SchedulePoint>,
        actualServiceSec: Int,
    ): Boolean {
        if (t.noSchedule == true || t.frequencyBased == true) return false
        val first = schedule.firstOrNull() ?: return false
        val departureSec = first.departureSec ?: first.arrivalSec ?: return false
        val firstStopM = geom.stopPathCumM.getOrElse(1) { 0.0 }
        val atFirstStop = sm.stopPathIndex == 0 || sm.distanceAlongTripM <= firstStopM + AT_FIRST_STOP_RADIUS_M
        return atFirstStop && actualServiceSec - departureSec > cfg.maxFirstStopLatenessSec
    }

    /**
     * Whether the vehicle is standing at [t]'s terminus after the trip is over (past its scheduled
     * end + `match.tripEndAdvanceGraceSec`) without having just arrived there on it. The arrival
     * itself still matches, however late; but a vehicle already at the terminus on this trip, or
     * one found there from elsewhere, isn't running it. Without this, a parked vehicle stays on a
     * finished trip with its delay growing until the trip leaves the candidate window
     * (`match.candidateTimeSlackSec`).
     */
    private fun finishedTrip(
        t: TripRef,
        geom: PatternGeometry,
        sm: SpatialMatch,
        prev: VehicleStateView?,
        actualServiceSec: Int,
    ): Boolean {
        if (t.noSchedule == true || t.frequencyBased == true) return false
        val endSec = t.endTimeSec ?: return false
        val terminusM = geom.line.lengthM - cfg.backtrackToleranceM
        if (sm.distanceAlongTripM < terminusM || actualServiceSec <= endSec + cfg.tripEndAdvanceGraceSec) return false
        val arrivingNow = prev?.tripRowId == t.id && (prev.distanceAlongTripM ?: 0.0) < terminusM
        return !arrivingNow
    }

    private fun continuityOf(
        t: TripRef,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): Double {
        if (prev?.tripRowId == null) return 0.0
        if (t.id == prev.tripRowId) return 1.0
        val prevBt = ctx.blockTripOf(prev.tripRowId!!) ?: return 0.0
        val next = ctx.nextBlockTrip(prevBt.blockId, prevBt.listIndex) ?: return 0.0
        return if (next.tripId == t.id) 0.5 else 0.0
    }

    private fun toMatched(
        trip: TripRef,
        geom: PatternGeometry,
        sm: SpatialMatch,
        adherence: Int?,
        score: Double,
        ctx: MatchContext,
    ) = MatchOutcome.Matched(
        tripRowId = trip.id,
        blockPk = ctx.blockTripOf(trip.id)?.blockId,
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
