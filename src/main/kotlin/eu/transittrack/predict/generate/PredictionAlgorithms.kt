package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictionAlgorithm

/**
 * Walks the current trip's remaining stop paths (from [fromStopPathIndex] to its last stop),
 * then follows the trip's block up to 2 more block-trips, each contributing all of its stop
 * paths from index 0. Stops early (not an error) once the block runs out of trips.
 */
fun buildHorizon(
    currentTripRowId: Long,
    currentTripPatternId: Long,
    fromStopPathIndex: Int,
    ctx: MatchContext,
): List<HorizonStop> {
    val stops = ArrayList<HorizonStop>()
    var tripRowId = currentTripRowId
    var patternId = currentTripPatternId
    var fromIndex = fromStopPathIndex
    var hops = 0
    while (hops <= 2) {
        val geom = ctx.patternGeometry(patternId)
        val stopCount = geom?.stopPathCumM?.size ?: 0
        for (i in fromIndex until stopCount) stops.add(HorizonStop(tripRowId, patternId, i))
        if (hops == 2) break
        val bt = ctx.blockTripOf(tripRowId) ?: break
        val next = ctx.nextBlockTrip(bt.blockId, bt.listIndex) ?: break
        val nextTrip = ctx.trip(next.tripId) ?: break
        val nextPatternId = nextTrip.tripPatternId ?: break
        tripRowId = next.tripId
        patternId = nextPatternId
        fromIndex = 0
        hops++
    }
    return stops
}

/** Converts a GTFS-style service-day-relative second offset into an absolute instant. */
fun serviceSecToInstant(
    serviceDate: LocalDate,
    sec: Int,
    zone: ZoneId,
): Instant = serviceDate.atStartOfDay(zone).plusSeconds(sec.toLong()).toInstant()

/**
 * Projects each horizon stop's scheduled time forward by the vehicle's current schedule
 * adherence. `AvlMatchContext`'s upstream `TemporalMatcher.adherenceSec` is defined as
 * `actualServiceSec - scheduledSec`, so a positive value means the vehicle is running LATE;
 * the forward projection is therefore `predictedSec = scheduledSec + adherenceSec`.
 */
@Component
class ScheduleAdherenceAlgorithm : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.SCHEDULE_ADHERENCE

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> {
        val predictions = ArrayList<GeneratedPrediction>()
        for (stop in horizon) {
            val schedulePoint =
                ctx
                    .scheduleOf(stop.tripRowId)
                    .firstOrNull { it.stopPathIndex == stop.stopPathIndex } ?: continue
            if (schedulePoint.arrivalSec == null && schedulePoint.departureSec == null) continue

            val predictedArrivalTs =
                schedulePoint.arrivalSec?.let { serviceSecToInstant(serviceDate, it + adherenceSec, ctx.zone) }
            val predictedDepartureTs =
                schedulePoint.departureSec?.let { serviceSecToInstant(serviceDate, it + adherenceSec, ctx.zone) }

            predictions.add(
                GeneratedPrediction(
                    stopPathIndex = stop.stopPathIndex,
                    tripRowId = stop.tripRowId,
                    tripPatternId = stop.tripPatternId,
                    predictedArrivalTs = predictedArrivalTs,
                    predictedDepartureTs = predictedDepartureTs,
                    confidenceSec = null,
                ),
            )
        }
        return predictions
    }
}
