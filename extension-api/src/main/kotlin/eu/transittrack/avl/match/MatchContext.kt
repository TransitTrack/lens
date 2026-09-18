package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import eu.transittrack.Point
import eu.transittrack.schedule.model.BlockTripRef
import eu.transittrack.schedule.model.StopPathRef
import eu.transittrack.schedule.model.TripRef

/**
 * Lean, extension-facing view over one GTFS revision's derived schedule. Mirrors every accessor
 * `eu.transittrack.avl.match.AvlMatchContext` (core) exposes, with JPA entities replaced by their
 * `*Ref` DTO equivalents. Core's `AvlMatchContext` implements this directly; nothing about its
 * internal caching changes.
 */
interface MatchContext {
    val revisionId: Long
    val zone: ZoneId

    fun patternGeometry(tripPatternId: Long): PatternGeometry?

    fun scheduleOf(tripRowId: Long): List<SchedulePoint>

    fun trip(tripRowId: Long): TripRef?

    fun tripByGtfsId(tripId: String): TripRef?

    fun blockTripOf(tripRowId: Long): BlockTripRef?

    fun nextBlockTrip(
        blockPk: Long,
        listIndex: Int,
    ): BlockTripRef?

    fun activeServiceIds(date: LocalDate): Set<String>

    fun candidateTrips(serviceIds: Set<String>): List<TripRef>

    fun candidateTripsForRoute(
        routeId: String,
        serviceIds: Set<String>,
    ): List<TripRef>

    fun patternStops(tripPatternId: Long): List<StopPathRef>

    fun patternExtentWithin(
        tripPatternId: Long,
        p: Point,
        distanceM: Double,
    ): Boolean
}
