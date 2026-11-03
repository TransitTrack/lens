package eu.transittrack.predict.smile

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.match.cache.CachedAgencyReader
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.generate.seedStopToStopSec
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionModelRepository
import eu.transittrack.predict.model.PredictionModelRow
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Trains each AVL feed's SMILE_GBM model from its recent `avl_stop_crossing` rows, stores it in
 * `prediction_model` (keyed by `avl_feed.code`) and publishes it to this node's [SmileModelStore];
 * other nodes pick it up on their next [SmileModelStore.refresh].
 *
 * Runs on the predictor role, once per cluster (ShedLock). A feed whose run fails, or that has fewer
 * than `smile.minTrainingRows` usable crossings, keeps its previous model.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.PREDICTOR)
class SmileTrainingJob(
    private val feeds: AvlFeedRepository,
    private val crossings: AvlStopCrossingRepository,
    private val models: PredictionModelRepository,
    private val travelTimes: CachedTravelTimesReader,
    private val agencies: CachedAgencyReader,
    private val store: SmileModelStore,
    private val props: PredictProperties,
    private val metrics: TransitTrackMetrics,
    private val clock: Clock,
    private val trainer: SmileTrainer = SmileTrainer(props.smile),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    enum class Outcome { TRAINED, SKIPPED, TOO_FEW_ROWS, FAILED }

    @Scheduled(cron = "\${transittrack.predict.smile.train-cron:0 30 3 * * *}")
    @SchedulerLock(name = "predict-smile-train", lockAtMostFor = "PT6H", lockAtLeastFor = "PT5M")
    fun trainAll() {
        val start = clock.instant()
        val outcomes =
            runCatching {
                feeds
                    .findAllEnabled()
                    .filter { PredictionAlgorithm.SMILE_GBM in it.predictionAlgorithms }
                    .map { trainFeed(it.code) }
            }
        outcomes.exceptionOrNull()?.let { log.error("smile training: could not list feeds", it) }
        val trained = outcomes.getOrNull()?.count { it == Outcome.TRAINED }
        metrics.scheduledJobFinished(
            job = "predict_smile_train",
            outcome =
                if (outcomes.getOrNull()?.none { it == Outcome.FAILED } == true) {
                    TransitTrackMetrics.Outcome.SUCCESS
                } else {
                    TransitTrackMetrics.Outcome.FAILED
                },
            elapsed = Duration.between(start, clock.instant()),
            items = trained?.toLong(),
        )
    }

    /**
     * Trains, stores and publishes [feedCode]'s model. Never throws: a failure is logged and the
     * feed keeps its previous model.
     */
    fun trainFeed(feedCode: String): Outcome =
        try {
            train(feedCode)
        } catch (e: Exception) {
            log.error("smile training: feed {} failed, keeping its previous model", feedCode, e)
            Outcome.FAILED
        }

    private fun train(feedCode: String): Outcome {
        val feed = feeds.findByCode(feedCode)
        if (feed == null || PredictionAlgorithm.SMILE_GBM !in feed.predictionAlgorithms) return Outcome.SKIPPED

        val trainedAt = clock.instant()
        val rows =
            crossings.training(
                feedCode = feedCode,
                since = trainedAt.minus(Duration.ofDays(props.smile.trainingDays.toLong())),
                maxReportGapSec = props.learn.maxReliableReportGapSec,
                maxTravelSec = props.learn.maxPlausibleTravelTimeSec,
                limit = props.smile.maxTrainingRows,
            )
        val seeded = seeded(rows)
        // Routes of the usable rows only: a route whose crossings were all skipped gets no index.
        val routeIndex =
            seeded
                .mapNotNull { it.crossing.routeId }
                .distinct()
                .sorted()
                .withIndex()
                .associate { (i, routeId) -> routeId to i }
        val samples = seeded.map { it.sample(routeIndex) }

        val fresh = trainer.train(samples, routeIndex)
        if (fresh == null) {
            log.info(
                "smile training: feed {} has {} usable of {} crossings, below the minimum {}; keeping its previous model",
                feedCode, samples.size, rows.size, props.smile.minTrainingRows,
            )
            return Outcome.TOO_FEW_ROWS
        }
        // Publish what a reload of the stored bytes gives: proves them readable, and the restored
        // instance holds none of the training-sized working arrays.
        val bytes = fresh.toBytes()
        val model = StopToStopModel.fromBytes(bytes)
        models.save(PredictionModelRow(feedCode, PredictionAlgorithm.SMILE_GBM, trainedAt, samples.size, bytes))
        store.put(feedCode, model, trainedAt)
        log.info(
            "smile training: feed {} trained on {} samples ({} routes) in {} s, {} bytes",
            feedCode, samples.size, routeIndex.size, Duration.between(trainedAt, clock.instant()).seconds, bytes.size,
        )
        return Outcome.TRAINED
    }

    /** A crossing usable for training, with its travel time, schedule seed and agency zone. */
    private class SeededCrossing(
        val crossing: AvlStopCrossing,
        val travelSec: Double,
        val seedSec: Double,
        val zone: ZoneId,
    ) {
        /**
         * Crossings don't store their service date, so it is inferred from `observed_at` and the
         * trip's start the way the matcher infers it for a report without a start date
         * ([inferServiceDate]): the local date in the revision's agency zone, a day earlier for a
         * trip started late the night before. Serving passes the service date its match resolved;
         * the two differ only for a report whose descriptor names another start date.
         */
        fun sample(routeIndex: Map<String, Int>): TrainingSample {
            val c = crossing
            // When the vehicle passed the previous stop: the moment the learners bucket a sample by.
            val leftPreviousStopAt: Instant = c.observedAt.minusMillis((travelSec * 1000).toLong())
            return TrainingSample(
                features =
                    StopToStopFeatures.featuresFor(
                        serviceDate = inferServiceDate(c.observedAt, c.tripStartSec, zone),
                        zone = zone,
                        leftPreviousStopAt = leftPreviousStopAt,
                        stopPathIndex = c.stopPathIndex,
                        seedSec = seedSec,
                        tripStartSec = c.tripStartSec,
                        directionId = c.directionId,
                        routeId = c.routeId,
                        routeIndex = routeIndex,
                    ),
                travelSec = travelSec,
            )
        }
    }

    /** The crossings whose pattern has a schedule seed for their stop path; the others are skipped. */
    private fun seeded(rows: List<AvlStopCrossing>): List<SeededCrossing> {
        // Per-run memos: each pattern and revision is looked up once per run, cache or not.
        val seedsByPattern = HashMap<Pair<Long, Long>, Map<Int, TravelTimesForStopPath>>()
        val zones = HashMap<Long, ZoneId>()
        return rows.mapNotNull { c ->
            val travelSec = c.observedTravelTimeSec ?: return@mapNotNull null
            val seeds = seedsByPattern.getOrPut(c.revisionId to c.tripPatternId) { travelTimes.byStopPath(c.revisionId, c.tripPatternId) }
            val seedSec = seedStopToStopSec(seeds, c.stopPathIndex) ?: return@mapNotNull null
            val zone = zones.getOrPut(c.revisionId) { agencies.timezoneOf(c.revisionId) ?: ZoneId.systemDefault() }
            SeededCrossing(c, travelSec, seedSec, zone)
        }
    }
}
