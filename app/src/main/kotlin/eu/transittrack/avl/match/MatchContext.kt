package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import eu.transittrack.geo.Point
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.StopPath

/**
 * Read view over one GTFS revision's derived schedule, as handed to matchers and prediction
 * strategies; [AvlMatchContext] is the cached implementation, tests wrap it with delegation. Returned [Trip] / [BlockTrip] / [StopPath] instances are shared,
 * revision-scoped cache entries: read them, never mutate them.
 */
interface MatchContext {
    val revisionId: Long
    val zone: ZoneId

    fun patternGeometry(tripPatternId: Long): PatternGeometry?

    fun scheduleOf(tripRowId: Long): List<SchedulePoint>

    fun trip(tripRowId: Long): Trip?

    fun tripByGtfsId(tripId: String): Trip?

    fun blockTripOf(tripRowId: Long): BlockTrip?

    fun nextBlockTrip(
        blockPk: Long,
        listIndex: Int,
    ): BlockTrip?

    fun activeServiceIds(date: LocalDate): Set<String>

    fun candidateTrips(serviceIds: Set<String>): List<Trip>

    fun candidateTripsForRoute(
        routeId: String,
        serviceIds: Set<String>,
    ): List<Trip>

    fun patternStops(tripPatternId: Long): List<StopPath>

    fun patternExtentWithin(
        tripPatternId: Long,
        p: Point,
        distanceM: Double,
    ): Boolean
}
