package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.Point
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.gtfs.model.Trip

/** Result of matching one AVL report against the derived schedule. */
sealed interface MatchOutcome {
    data class Matched(
        val tripRowId: Long,
        val blockPk: Long?,
        val tripPatternId: Long,
        val stopPathIndex: Int,
        val distanceAlongTripM: Double,
        val deviationM: Double,
        val scheduleAdherenceSec: Int?,
        val snapped: Point,
        val heading: Double?,
        val score: Double?,
        val revisionId: Long,
    ) : MatchOutcome

    data object Failed : MatchOutcome

    data object Skipped : MatchOutcome
}

interface VehicleMatcher {
    val mode: AvlAssignmentMode

    fun match(
        report: AvlReportRow,
        prev: VehicleState?,
        ctx: AvlMatchContext,
    ): MatchOutcome
}

/**
 * `report.descStartDate` wins; else the report ts local date, shifted back a day when the report
 * ts-of-day is well before the trip's start (a trip starting 23:30 seen at 00:10 = previous day).
 */
fun inferServiceDate(
    report: AvlReportRow,
    trip: Trip,
    zone: ZoneId,
): LocalDate {
    report.descStartDate?.let { return it }
    val zoned = report.ts.atZone(zone)
    val localDate = zoned.toLocalDate()
    val secOfDay = zoned.toLocalTime().toSecondOfDay()
    val start = trip.startTimeSec
    return if (start != null && secOfDay + 14_400 < start) localDate.minusDays(1) else localDate
}

/**
 * `prev` is on `trip`, at/near its end, and the report ts is past the trip end (plus grace) → resolve
 * the next `block_trip` and its geometry. `null` when any precondition fails. Shared by
 * [TrustDescriptorMatcher] and the full-inference matcher.
 */
internal fun tryBlockAdvance(
    prev: VehicleState,
    geom: PatternGeometry,
    trip: Trip,
    actualServiceSec: Int,
    ctx: AvlMatchContext,
    backtrackToleranceM: Double,
    tripEndAdvanceGraceSec: Int,
): Pair<Trip, PatternGeometry>? {
    if (prev.tripRowId != trip.id) return null
    val along = prev.distanceAlongTripM ?: return null
    if (along < geom.line.lengthM - backtrackToleranceM) return null
    val end = trip.endTimeSec ?: return null
    if (actualServiceSec <= end + tripEndAdvanceGraceSec) return null
    val bt = ctx.blockTripOf(trip.id!!) ?: return null
    val next = ctx.nextBlockTrip(bt.blockId, bt.listIndex) ?: return null
    val nextTrip = ctx.trip(next.tripId) ?: return null
    val nextPatternId = nextTrip.tripPatternId ?: return null
    val nextGeom = ctx.patternGeometry(nextPatternId) ?: return null
    return nextTrip to nextGeom
}
