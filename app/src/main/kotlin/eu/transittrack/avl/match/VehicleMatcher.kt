package eu.transittrack.avl.match

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

import eu.transittrack.gtfs.model.Trip

/** Assigns a report to a trip for one [AvlAssignmentMode]; core picks the bean whose [mode] matches the feed. */
interface VehicleMatcher {
    val mode: AvlAssignmentMode

    fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome
}

/**
 * The instant a service day's GTFS times count from: noon minus 12 hours, per the GTFS reference -
 * not midnight. The two differ by an hour on the days DST starts or ends; counting from midnight
 * shifts every time on those days by an hour.
 */
fun serviceDayStart(
    serviceDate: LocalDate,
    zone: ZoneId,
): Instant =
    serviceDate
        .atTime(LocalTime.NOON)
        .atZone(zone)
        .toInstant()
        .minus(Duration.ofHours(12))

/**
 * `report.descStartDate` wins; else the report ts local date, shifted back a day when the report
 * ts-of-day is well before the trip's start (a trip starting 23:30 seen at 00:10 = previous day).
 */
fun inferServiceDate(
    report: AvlReportView,
    tripStartSec: Int?,
    zone: ZoneId,
): LocalDate {
    report.descStartDate?.let { return it }
    val zoned = report.ts.atZone(zone)
    val localDate = zoned.toLocalDate()
    val secOfDay = zoned.toLocalTime().toSecondOfDay()
    return if (tripStartSec != null && secOfDay + 14_400 < tripStartSec) localDate.minusDays(1) else localDate
}

/**
 * `prev` is on `trip`, at/near its end, and the report ts is past the trip end (plus grace) → resolve
 * the next `block_trip` and its geometry. `null` when any precondition fails. Shared by
 * [TrustDescriptorMatcher] and the full-inference matcher.
 */
internal fun tryBlockAdvance(
    prev: VehicleStateView,
    geom: PatternGeometry,
    trip: Trip,
    actualServiceSec: Int,
    ctx: MatchContext,
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
