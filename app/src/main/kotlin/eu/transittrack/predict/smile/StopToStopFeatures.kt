package eu.transittrack.predict.smile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket

/**
 * Feature vector for the stop-to-stop travel-time model. Only features available both in
 * `avl_stop_crossing` (training) and at prediction time are used; the vehicle's adherence is
 * deliberately absent because crossings don't store it.
 */
object StopToStopFeatures {
    val NAMES: List<String> =
        listOf("stopPathIndex", "seedSec", "dayType", "timeBand", "tripStartSec", "directionId", "routeIndex")

    /** The `routeIndex` feature of a route the model wasn't trained on, or of a crossing without a route. */
    const val UNKNOWN_ROUTE = -1

    /**
     * The features of one stop-to-stop traversal: the single entry point both training
     * ([SmileTrainingJob]) and serving use, so the two can't drift apart. The day type and time band
     * are those of [leftPreviousStopAt] on [serviceDate] (see [TravelTimeBucket.at]) - when the
     * vehicle left (or, in training, was observed passing) the previous stop - the same moment the
     * other learners bucket samples by and predictions look a stop path's time up by. [routeId] is
     * mapped through [routeIndex] (the model's), [UNKNOWN_ROUTE] when absent.
     */
    fun featuresFor(
        serviceDate: LocalDate,
        zone: ZoneId,
        leftPreviousStopAt: Instant,
        stopPathIndex: Int,
        seedSec: Double,
        tripStartSec: Int?,
        directionId: Int?,
        routeId: String?,
        routeIndex: Map<String, Int>,
    ): DoubleArray {
        val bucket = TravelTimeBucket.at(serviceDate, zone, leftPreviousStopAt)
        return vector(
            stopPathIndex = stopPathIndex,
            seedSec = seedSec,
            dayType = bucket.dayType,
            timeBand = bucket.timeBand,
            tripStartSec = tripStartSec,
            directionId = directionId,
            routeIndex = routeId?.let { routeIndex[it] } ?: UNKNOWN_ROUTE,
        )
    }

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
