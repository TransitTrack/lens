package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.avl.match.PatternGeometry
import eu.transittrack.avl.match.serviceDayStart
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.FIRST_STOP_RADIUS_M
import eu.transittrack.predict.learn.stopDistanceM
import eu.transittrack.predict.learn.stopReachedAtM

/**
 * Walks the current trip's remaining stop paths (from [fromStopPathIndex] to its last stop), then
 * follows the trip's block up to 2 more block-trips, each contributing all of its stop paths from
 * index 0. Stops early once the block runs out of trips or requires an unmodelled deadhead.
 *
 * The first horizon stop carries the share of its stop path still ahead of [distanceAlongTripM].
 * A vehicle still standing at its trip's first stop (within [FIRST_STOP_RADIUS_M] of the start)
 * gets that first stop in its horizon, so strategies can hold it until its scheduled departure; one
 * that already reached its trip's last stop (see [stopReachedAtM]) no longer has it in its horizon.
 */
fun buildHorizon(
    currentTripRowId: Long,
    currentTripPatternId: Long,
    fromStopPathIndex: Int,
    distanceAlongTripM: Double,
    ctx: MatchContext,
): List<HorizonStop> {
    val stops = mutableListOf<HorizonStop>()
    var tripRowId = currentTripRowId
    var patternId = currentTripPatternId
    var hops = 0
    while (hops <= 2) {
        val geom = ctx.patternGeometry(patternId)
        val stopCount = geom?.stopPathCumM?.size ?: 0
        if (hops == 0) {
            val standingAtStart = fromStopPathIndex <= 1 && distanceAlongTripM <= FIRST_STOP_RADIUS_M
            val fromIndex = if (standingAtStart) 0 else fromStopPathIndex
            val reachedLastStop =
                geom != null && stopCount > 1 && distanceAlongTripM >= stopReachedAtM(stopCount - 1, geom.stopPathCumM, geom.line.lengthM)
            val untilIndex = if (reachedLastStop) stopCount - 1 else stopCount
            for (i in fromIndex until untilIndex) {
                val fraction = if (i == fromIndex &&
                    !standingAtStart &&
                    geom != null
                ) {
                    remainingFraction(i, distanceAlongTripM, geom)
                } else {
                    1.0
                }
                stops.add(HorizonStop(tripRowId, patternId, i, fraction))
            }
        } else {
            for (i in 0 until stopCount) stops.add(HorizonStop(tripRowId, patternId, i, tripHop = hops))
        }
        if (hops == 2) break
        val bt = ctx.blockTripOf(tripRowId) ?: break
        if (bt.deadheadAfter == true) break
        val next = ctx.nextBlockTrip(bt.blockId, bt.listIndex) ?: break
        val nextTrip = ctx.trip(next.tripId) ?: break
        val nextPatternId = nextTrip.tripPatternId ?: break
        tripRowId = next.tripId
        patternId = nextPatternId
        hops++
    }
    return stops
}

private fun remainingFraction(
    stopPathIndex: Int,
    distanceAlongTripM: Double,
    geom: PatternGeometry,
): Double {
    val start = geom.stopPathCumM[stopPathIndex]
    val end = stopDistanceM(stopPathIndex, geom.stopPathCumM, geom.line.lengthM)
    if (end <= start) return 0.0
    return ((end - distanceAlongTripM) / (end - start)).coerceIn(0.0, 1.0)
}

/** Converts a GTFS-style service-day-relative second offset into an absolute instant. */
fun serviceSecToInstant(
    serviceDate: LocalDate,
    sec: Int,
    zone: ZoneId,
): Instant = serviceDayStart(serviceDate, zone).plusSeconds(sec.toLong())

/**
 * Whether a vehicle holds at this stop until its scheduled departure rather than leaving as soon
 * as it's ready: a trip's first stop. (`stop_path.wait_stop` isn't used: it's derived from GTFS
 * timepoints, which say a time is exact, not that vehicles wait for it - in feeds without a
 * timepoint column it's set on every stop.)
 */
internal fun holdsUntilScheduled(stop: HorizonStop): Boolean = stop.stopPathIndex == 0

/**
 * Projects each horizon stop's scheduled time forward by the vehicle's current schedule
 * adherence. `TemporalMatcher.adherenceSec` is defined as `actualServiceSec - scheduledSec`, so a
 * positive value means the vehicle is running LATE; the forward projection is therefore
 * `predictedSec = scheduledSec + delay`.
 *
 * The delay isn't carried blindly: a vehicle never leaves a trip's first stop before its scheduled
 * departure, so being early is absorbed there; and on the next block trip the vehicle starts from
 * where the previous trip's prediction left it, so lateness is absorbed by the scheduled layover
 * instead of being copied onto the next trip whole.
 *
 * Frequency-based trips (GTFS `frequencies.txt`) have no clock-time schedule to adhere to - their
 * stop times are offsets from whenever a run starts - so the walk stops at the first one.
 */
@Component
class ScheduleAdherenceAlgorithm : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.SCHEDULE_ADHERENCE

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> {
        var delaySec = adherenceSec ?: return emptyList()
        var previousTripRowId: Long? = null
        var lastArrivalTs: Instant? = null
        var lastDepartureTs: Instant? = null
        val predictions = arrayListOf<GeneratedPrediction>()
        for (stop in horizon) {
            if (stop.tripRowId != previousTripRowId && ctx.trip(stop.tripRowId)?.frequencyBased == true) break
            val schedulePoint =
                ctx
                    .scheduleOf(stop.tripRowId)
                    .firstOrNull { it.stopPathIndex == stop.stopPathIndex } ?: continue
            val scheduledArrivalSec = schedulePoint.arrivalSec ?: schedulePoint.departureSec ?: continue
            val scheduledDepartureSec = schedulePoint.departureSec ?: scheduledArrivalSec

            val startsNextTrip =
                previousTripRowId?.let { it != stop.tripRowId } ?: (stop.tripHop > 0)
            val arrival =
                if (startsNextTrip) {
                    maxOf(lastArrivalTs ?: startTs, lastDepartureTs ?: startTs)
                } else {
                    serviceSecToInstant(serviceDate, scheduledArrivalSec + delaySec, ctx.zone)
                }
            val scheduledDeparture = serviceSecToInstant(serviceDate, scheduledDepartureSec, ctx.zone)
            // On the next trip the vehicle leaves from where it actually arrives; the delay carried
            // over from the previous trip doesn't apply to it until it's re-derived below.
            var departure =
                if (startsNextTrip) {
                    maxOf(arrival, lastDepartureTs ?: startTs)
                } else {
                    maxOf(arrival, scheduledDeparture.plusSeconds(delaySec.toLong()))
                }
            if (holdsUntilScheduled(stop)) {
                departure = maxOf(departure, scheduledDeparture)
                delaySec = Duration.between(scheduledDeparture, departure).seconds.toInt()
            }

            predictions.add(
                GeneratedPrediction(
                    stopPathIndex = stop.stopPathIndex,
                    tripRowId = stop.tripRowId,
                    tripPatternId = stop.tripPatternId,
                    predictedArrivalTs = schedulePoint.arrivalSec?.let { arrival },
                    predictedDepartureTs = schedulePoint.departureSec?.let { departure },
                    confidenceSec = null,
                ),
            )
            previousTripRowId = stop.tripRowId
            lastArrivalTs = arrival
            lastDepartureTs = departure
        }
        return predictions
    }
}
