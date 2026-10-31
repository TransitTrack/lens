package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.avl.match.cache.CachedLearningKeyReader
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.learn.pickLearned
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.schedule.model.TravelTimesForStopPath

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

private fun estimatedDwellBeforeSec(
    seeds: Map<Int, TravelTimesForStopPath>,
    stopPathIndex: Int,
    stopToStopSec: Double,
): Double {
    val scheduledTotal = seedStopToStopSec(seeds, stopPathIndex) ?: return 0.0
    if (scheduledTotal <= 0.0) return 0.0
    return stopToStopSec * (dwellBeforeSec(seeds, stopPathIndex) / scheduledTotal).coerceIn(0.0, 1.0)
}

/**
 * A learned stop-to-stop time, with the variance of the next vehicle's actual time around it when the
 * learner tracks one.
 */
internal data class LearnedStopToStop(
    val sec: Double,
    val varianceSec2: Double?,
    val sampleCount: Long,
)

/**
 * Walks the horizon accumulating stop-to-stop times (learned for the day type and time band each
 * stop path is driven in, falling back to coarser roll-ups, else the schedule seed) from
 * [startTs]. Only the uncovered share of the vehicle's current stop path is counted. At a trip's
 * first stop the vehicle isn't predicted to leave before its scheduled departure, which also
 * carries it across into the next block trip (a frequency-based trip has no clock-time departure to
 * hold for). Learned arrival-to-arrival times are split into dwell and travel in the schedule's
 * proportions, so departures and subsequent arrivals describe the same timeline. Confidence is the
 * accumulated standard deviation, including [fallbackVarianceSec2] for unlearned legs when supplied.
 */
internal fun walkHorizon(
    horizon: List<HorizonStop>,
    serviceDate: LocalDate,
    startTs: Instant,
    ctx: MatchContext,
    seedReader: CachedTravelTimesReader,
    minBucketSamples: Int,
    fallbackVarianceSec2: Double? = null,
    learnedFor: (tripPatternId: Long) -> Map<Int, Map<TravelTimeBucket, LearnedStopToStop>>,
): List<GeneratedPrediction> {
    val learnedByPattern = HashMap<Long, Map<Int, Map<TravelTimeBucket, LearnedStopToStop>>>()

    // The last predicted pass: arrival at the previous stop, or the departure from it when it held.
    var passTs = startTs
    var leftPassedStop = true
    var varianceSum: Double? = null
    val out = ArrayList<GeneratedPrediction>()
    for (stop in horizon) {
        val seeds = seedReader.byStopPath(ctx.revisionId, stop.tripPatternId)
        val learned = learnedByPattern.getOrPut(stop.tripPatternId) { learnedFor(stop.tripPatternId) }
        val i = stop.stopPathIndex
        val previousDeparture =
            out.lastOrNull()?.takeIf { i == 0 && it.tripRowId != stop.tripRowId }?.predictedDepartureTs

        val arrival =
            if (i == 0) {
                if (previousDeparture != null) passTs = maxOf(passTs, previousDeparture)
                // Stop path 0 is zero-length: the vehicle is at (or, on the next block trip, already
                // standing at) the first stop.
                passTs
            } else {
                // Looked up by when the vehicle leaves the previous stop - a trip running into the
                // morning peak picks up peak times for its later stop paths.
                val bucket = TravelTimeBucket.at(serviceDate, ctx.zone, passTs)
                val learnedHere = learned[i]?.let { pickLearned(it, bucket, minBucketSamples) { l -> l.sampleCount } }
                val stopToStop = learnedHere?.sec ?: seedStopToStopSec(seeds, i) ?: break
                require(stopToStop.isFinite() && stopToStop >= 0.0) {
                    "Invalid stop-to-stop time for pattern ${stop.tripPatternId}, stop $i"
                }
                val dwellBefore = estimatedDwellBeforeSec(seeds, i, stopToStop)
                if (!leftPassedStop) {
                    val previous = out.lastOrNull()
                    if (previous != null && previous.tripRowId == stop.tripRowId && previous.stopPathIndex == i - 1) {
                        out[out.lastIndex] = previous.copy(
                            predictedDepartureTs = previous.predictedArrivalTs?.plusMillis((dwellBefore * 1000).toLong()),
                        )
                    }
                }
                // Once the vehicle has left the previous stop, its dwell there is behind it.
                val ahead = if (leftPassedStop) stopToStop - dwellBefore else stopToStop
                val travelSec = ahead.coerceAtLeast(0.0) * stop.remainingFraction
                (learnedHere?.varianceSec2 ?: fallbackVarianceSec2)?.let { v ->
                    require(v.isFinite() && v >= 0.0) { "Invalid travel-time variance for pattern ${stop.tripPatternId}, stop $i" }
                    varianceSum = (varianceSum ?: 0.0) + v * stop.remainingFraction * stop.remainingFraction
                }
                passTs.plusMillis((travelSec * 1000).toLong())
            }

        val dwellHere = (seeds[i]?.dwellTimeSec ?: 0).toLong()
        val holds = holdsUntilScheduled(stop)
        val scheduledDeparture =
            if (holds && ctx.trip(stop.tripRowId)?.frequencyBased != true) {
                ctx
                    .scheduleOf(stop.tripRowId)
                    .firstOrNull { it.stopPathIndex == i }
                    ?.departureSec
                    ?.let { serviceSecToInstant(serviceDate, it, ctx.zone) }
            } else {
                null
            }
        val departure = maxOf(arrival.plusSeconds(dwellHere), scheduledDeparture ?: Instant.MIN, previousDeparture ?: Instant.MIN)

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
    private val seeds: CachedTravelTimesReader,
    private val learningKeys: CachedLearningKeyReader,
    private val props: PredictProperties,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> =
        walkHorizon(horizon, serviceDate, startTs, ctx, seeds, props.learn.minBucketSamples) { patternId ->
            val key = learningKeys.keyOf(ctx.revisionId, patternId) ?: return@walkHorizon emptyMap()
            observations
                .findByPattern(key.gtfsFeedCode, key.patternKey)
                .groupBy { it.stopPathIndex }
                .mapValues { (_, rows) ->
                    rows.associate { TravelTimeBucket(it.dayType, it.timeBand) to LearnedStopToStop(it.meanSec, null, it.sampleCount) }
                }
        }
}

/**
 * Accumulates each stop path's learned Kalman stop-to-stop estimate (schedule seed fallback),
 * reporting `confidenceSec` as accumulated standard deviation, including fallback legs and evidence age. Each
 * leg's variance is the estimate's own uncertainty plus the trip-to-trip spread the filter assumes
 * for a single vehicle (its measurement noise): the estimate's uncertainty alone shrinks to a few
 * seconds once a stop path is well sampled, while any one vehicle still varies around it.
 */
@Component
class KalmanAlgorithm(
    private val states: KalmanTravelTimeStateRepository,
    private val seeds: CachedTravelTimesReader,
    private val learningKeys: CachedLearningKeyReader,
    private val props: PredictProperties,
) : PredictionStrategy {
    override val algorithm = PredictionAlgorithm.KALMAN

    override fun predict(
        horizon: List<HorizonStop>,
        serviceDate: LocalDate,
        adherenceSec: Int?,
        startTs: Instant,
        ctx: MatchContext,
    ): List<GeneratedPrediction> =
        walkHorizon(
            horizon,
            serviceDate,
            startTs,
            ctx,
            seeds,
            props.learn.minBucketSamples,
            props.learn.kalmanInitialVarianceSec2 + props.learn.kalmanMeasurementNoiseSec2,
        ) { patternId ->
            val key = learningKeys.keyOf(ctx.revisionId, patternId) ?: return@walkHorizon emptyMap()
            states
                .findByPattern(key.gtfsFeedCode, key.patternKey)
                .groupBy { it.stopPathIndex }
                .mapValues { (_, rows) ->
                    rows.associate {
                        TravelTimeBucket(it.dayType, it.timeBand) to
                            LearnedStopToStop(
                                it.estimateSec,
                                it.errorVariance +
                                    props.learn.kalmanMeasurementNoiseSec2 +
                                    Duration.between(it.updatedAt, startTs).seconds.coerceAtLeast(0) /
                                    3600.0 *
                                    props.learn.kalmanVarianceGrowthSec2PerHour,
                                it.sampleCount,
                            )
                    }
                }
        }
}
