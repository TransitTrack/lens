package eu.transittrack.predict.generate

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository

/**
 * Schedule-derived stop-to-stop time for stop path [stopPathIndex]: the time from passing the
 * previous stop to reaching this one - its travel time plus the previous stop's dwell. Stop path
 * 1 is measured from leaving the first stop, so it carries no dwell. This is the same quantity the
 * learners observe (see `PredictionService`), and the fallback when nothing is learned yet.
 */
fun seedStopToStopSec(
    seeds: Map<Int, TravelTimesForStopPath>,
    stopPathIndex: Int,
): Double? {
    val travel = seeds[stopPathIndex]?.travelTimeSec?.toDouble() ?: return null
    return travel + dwellBeforeSec(seeds, stopPathIndex)
}

/** Dwell at the stop before [stopPathIndex] that a stop-to-stop time includes. */
private fun dwellBeforeSec(
    seeds: Map<Int, TravelTimesForStopPath>,
    stopPathIndex: Int,
): Double = if (stopPathIndex >= 2) (seeds[stopPathIndex - 1]?.dwellTimeSec ?: 0).toDouble() else 0.0

/** A learned stop-to-stop time, with its variance when the learner tracks one. */
internal data class LearnedStopToStop(
    val sec: Double,
    val varianceSec2: Double?,
)

/**
 * Walks the horizon accumulating stop-to-stop times (learned, else the schedule seed) from
 * [startTs]. Only the uncovered share of the vehicle's current stop path is counted. At a trip's
 * first stop the vehicle isn't predicted to leave before its scheduled departure, which also
 * carries it across into the next block trip. Confidence, when any learned
 * variance is involved, is the standard deviation accumulated along the walk so far.
 */
internal fun walkHorizon(
    horizon: List<HorizonStop>,
    serviceDate: LocalDate,
    startTs: Instant,
    ctx: MatchContext,
    seedRepository: TravelTimesForStopPathRepository,
    learnedFor: (tripPatternId: Long) -> Map<Int, LearnedStopToStop>,
): List<GeneratedPrediction> {
    val seedsByPattern = HashMap<Long, Map<Int, TravelTimesForStopPath>>()
    val learnedByPattern = HashMap<Long, Map<Int, LearnedStopToStop>>()

    // The last predicted pass: arrival at the previous stop, or the departure from it when it held.
    var passTs = startTs
    var leftPassedStop = true
    var varianceSum: Double? = null
    val out = ArrayList<GeneratedPrediction>()
    for (stop in horizon) {
        val seeds =
            seedsByPattern.getOrPut(stop.tripPatternId) {
                seedRepository.findByTripPatternOrdered(ctx.revisionId, stop.tripPatternId).associateBy { it.stopPathIndex }
            }
        val learned = learnedByPattern.getOrPut(stop.tripPatternId) { learnedFor(stop.tripPatternId) }
        val i = stop.stopPathIndex

        val arrival =
            if (i == 0) {
                // Stop path 0 is zero-length: the vehicle is at (or, on the next block trip, already
                // standing at) the first stop.
                passTs
            } else {
                val learnedHere = learned[i]
                val stopToStop = learnedHere?.sec ?: seedStopToStopSec(seeds, i) ?: break
                // Once the vehicle has left the previous stop, its dwell there is behind it.
                val ahead = if (leftPassedStop) stopToStop - dwellBeforeSec(seeds, i) else stopToStop
                val travelSec = ahead.coerceAtLeast(0.0) * stop.remainingFraction
                learnedHere?.varianceSec2?.let { v ->
                    varianceSum = (varianceSum ?: 0.0) + v * stop.remainingFraction * stop.remainingFraction
                }
                passTs.plusMillis((travelSec * 1000).toLong())
            }

        val dwellHere = (seeds[i]?.dwellTimeSec ?: 0).toLong()
        val holds = holdsUntilScheduled(stop)
        val scheduledDeparture =
            if (holds) {
                ctx
                    .scheduleOf(stop.tripRowId)
                    .firstOrNull { it.stopPathIndex == i }
                    ?.departureSec
                    ?.let { serviceSecToInstant(serviceDate, it, ctx.zone) }
            } else {
                null
            }
        val departure = maxOf(arrival.plusSeconds(dwellHere), scheduledDeparture ?: Instant.MIN)

        out.add(
            GeneratedPrediction(
                stopPathIndex = i,
                tripRowId = stop.tripRowId,
                tripPatternId = stop.tripPatternId,
                predictedArrivalTs = arrival,
                predictedDepartureTs = departure,
                confidenceSec = varianceSum?.let { sqrt(it).roundToInt() },
            ),
        )
        if (holds) {
            passTs = departure
            leftPassedStop = true
        } else {
            passTs = arrival
            leftPassedStop = false
        }
    }
    return out
}

/** Accumulates each stop path's learned windowed-mean stop-to-stop time (schedule seed fallback). */
@Component
class HistoricalAverageAlgorithm(
    private val observations: TravelTimeObservationRepository,
    private val seeds: TravelTimesForStopPathRepository,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> =
        walkHorizon(horizon, serviceDate, startTs, ctx, seeds) { patternId ->
            observations.findByPattern(patternId).associate { it.stopPathIndex to LearnedStopToStop(it.meanSec, null) }
        }
}

/**
 * Accumulates each stop path's learned Kalman stop-to-stop estimate (schedule seed fallback),
 * reporting `confidenceSec` as the accumulated standard deviation of the learned legs so far.
 */
@Component
class KalmanAlgorithm(
    private val states: KalmanTravelTimeStateRepository,
    private val seeds: TravelTimesForStopPathRepository,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.KALMAN

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> =
        walkHorizon(horizon, serviceDate, startTs, ctx, seeds) { patternId ->
            states.findByPattern(patternId).associate { it.stopPathIndex to LearnedStopToStop(it.estimateSec, it.errorVariance) }
        }
}
