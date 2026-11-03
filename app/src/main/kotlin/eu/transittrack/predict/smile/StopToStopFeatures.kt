package eu.transittrack.predict.smile

import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand

/**
 * Feature vector for the stop-to-stop travel-time model. Only features available both in
 * `avl_stop_crossing` (training) and at prediction time are used; the vehicle's adherence is
 * deliberately absent because crossings don't store it.
 */
object StopToStopFeatures {
    val NAMES: List<String> =
        listOf("stopPathIndex", "seedSec", "dayType", "timeBand", "tripStartSec", "directionId", "routeIndex")

    fun vector(
        stopPathIndex: Int,
        seedSec: Double,
        dayType: DayType,
        timeBand: TimeBand,
        tripStartSec: Int?,
        directionId: Int?,
        routeIndex: Int,
    ): DoubleArray =
        doubleArrayOf(
            stopPathIndex.toDouble(),
            seedSec,
            dayType.ordinal.toDouble(),
            timeBand.ordinal.toDouble(),
            (tripStartSec ?: -1).toDouble(),
            (directionId ?: -1).toDouble(),
            routeIndex.toDouble(),
        )
}
