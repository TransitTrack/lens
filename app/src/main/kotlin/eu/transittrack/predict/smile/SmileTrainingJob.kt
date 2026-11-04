package eu.transittrack.predict.smile

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import jakarta.annotation.PreDestroy

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
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionModelRepository
import eu.transittrack.predict.model.PredictionModelRow
import eu.transittrack.predict.model.TrainingCrossings
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Trains each AVL feed's SMILE_GBM model from its recent `avl_stop_crossing` rows, stores it in
 * `prediction_model` (keyed by `avl_feed.code`) and publishes it to this node's [SmileModelStore];
 * other nodes pick it up on their next [SmileModelStore.refresh].
 *
 * Runs on the predictor role, once per cluster (ShedLock). A feed whose run fails, or that has fewer
 * than `smile.minTrainingRows` usable crossings, keeps its previous model. The fits run one at a
 * time on a dedicated low-priority thread ([lowPriorityPool]).
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

    private val fitPool: ForkJoinPool = lowPriorityPool()

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
        val data = trainingData(feedCode, trainedAt)
        val fresh = fit(data)
        if (fresh == null) {
            log.info(
                "smile training: feed {} has {} usable of {} crossings, below the minimum {}; keeping its previous model",
                feedCode, data.samples.size, data.crossingCount, props.smile.minTrainingRows,
            )
            return Outcome.TOO_FEW_ROWS
        }
        // Publish what a reload of the stored bytes gives: proves them readable, and the restored
        // instance holds none of the training-sized working arrays.
        val bytes = fresh.toBytes()
        val model = StopToStopModel.fromBytes(bytes)
        models.save(PredictionModelRow(feedCode, PredictionAlgorithm.SMILE_GBM, trainedAt, data.samples.size, bytes))
        store.put(feedCode, model, trainedAt)
        log.info(
            "smile training: feed {} trained on {} samples ({} routes) in {} s, {} bytes",
            feedCode, data.samples.size, data.routeIndex.size, Duration.between(trainedAt, clock.instant()).seconds, bytes.size,
        )
        return Outcome.TRAINED
    }

    /**
     * Fits on [fitPool] and waits for it, so the caller (and the scheduler lock [trainAll] holds)
     * still sees the run end only once the model is built.
     */
    private fun fit(data: TrainingData): StopToStopModel? {
        val task = fitPool.submit(Callable { trainer.train(data.samples, data.routeIndex) })
        try {
            return task.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            throw e
        }
    }

    @PreDestroy
    fun close() {
        fitPool.shutdownNow()
    }

    /** The training samples of [feedCode], and how many crossings they were built from. */
    private class TrainingData(
        val samples: TrainingSet,
        val routeIndex: Map<String, Int>,
        val crossingCount: Int,
    )

    /**
     * Builds the samples from the feed's crossings, skipping those whose pattern has no schedule seed
     * for their stop path. The crossings are only referenced from here, so they can be collected
     * before the fit starts.
     */
    private fun trainingData(
        feedCode: String,
        trainedAt: Instant,
    ): TrainingData {
        val rows =
            crossings.training(
                feedCode = feedCode,
                since = trainedAt.minus(Duration.ofDays(props.smile.trainingDays.toLong())),
                maxReportGapSec = props.learn.maxReliableReportGapSec,
                maxTravelSec = props.learn.maxPlausibleTravelTimeSec,
                limit = props.smile.maxTrainingRows,
            )
        // Per-run memos: each pattern and revision is looked up once per run, cache or not.
        val seedsByPattern = HashMap<Pair<Long, Long>, Map<Int, TravelTimesForStopPath>>()
        val zones = HashMap<Long, ZoneId>()
        // NaN: no schedule seed, the crossing is skipped.
        val seedSecs =
            DoubleArray(rows.size) { i ->
                val revisionId = rows.revisionId(i)
                val seeds = seedsByPattern.getOrPut(revisionId to rows.tripPatternId(i)) {
                    travelTimes
                        .byStopPath(revisionId, rows.tripPatternId(i))
                }
                seedStopToStopSec(seeds, rows.stopPathIndex(i)) ?: Double.NaN
            }
        // Routes of the usable rows only: a route whose crossings were all skipped gets no index.
        val routeIndex =
            (0 until rows.size)
                .filter { !seedSecs[it].isNaN() }
                .mapNotNullTo(HashSet()) { rows.routeId(it) }
                .sorted()
                .withIndex()
                .associate { (i, routeId) -> routeId to i }
        val samples = TrainingSet(seedSecs.count { !it.isNaN() })
        for (i in 0 until rows.size) {
            if (seedSecs[i].isNaN()) continue
            val zone = zones.getOrPut(rows.revisionId(i)) { agencies.timezoneOf(rows.revisionId(i)) ?: ZoneId.systemDefault() }
            samples.add(features(rows, i, seedSecs[i], zone, routeIndex), rows.travelSec(i))
        }
        return TrainingData(samples, routeIndex, rows.size)
    }

    /**
     * The features of crossing [i]. Crossings don't store their service date, so it is inferred from
     * `observed_at` and the trip's start the way the matcher infers it for a report without a start
     * date ([inferServiceDate]): the local date in the revision's agency zone, a day earlier for a
     * trip started late the night before. Serving passes the service date its match resolved; the two
     * differ only for a report whose descriptor names another start date.
     */
    private fun features(
        rows: TrainingCrossings,
        i: Int,
        seedSec: Double,
        zone: ZoneId,
        routeIndex: Map<String, Int>,
    ): DoubleArray {
        val observedAt = rows.observedAt(i)
        val tripStartSec = rows.tripStartSec(i)
        // When the vehicle passed the previous stop: the moment the learners bucket a sample by.
        val leftPreviousStopAt = observedAt.minusMillis((rows.travelSec(i) * 1000).toLong())
        return StopToStopFeatures.featuresFor(
            serviceDate = inferServiceDate(observedAt, tripStartSec, zone),
            zone = zone,
            leftPreviousStopAt = leftPreviousStopAt,
            stopPathIndex = rows.stopPathIndex(i),
            seedSec = seedSec,
            tripStartSec = tripStartSec,
            directionId = rows.directionId(i),
            routeId = rows.routeId(i),
            routeIndex = routeIndex,
        )
    }

    private companion object {
        /**
         * One low-priority daemon platform thread for the fits. A fork-join pool, so the parallel
         * split search inside Smile's tree building runs here too, rather than on the common pool;
         * with parallelism 1 a fit takes at most one core, and never a virtual-thread carrier (the
         * scheduler runs on virtual threads) that the 5 s prediction loop needs.
         */
        fun lowPriorityPool(): ForkJoinPool =
            ForkJoinPool(
                1,
                { pool ->
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply {
                        name = "smile-train-$poolIndex"
                        isDaemon = true
                        priority = Thread.MIN_PRIORITY
                    }
                },
                null,
                false,
            )
    }
}
