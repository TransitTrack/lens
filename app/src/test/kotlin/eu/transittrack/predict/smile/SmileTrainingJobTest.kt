package eu.transittrack.predict.smile

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.avl.match.cache.CachedAgencyReader
import eu.transittrack.avl.match.cache.CachedTravelTimesReader
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.predict.PredictProperties
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionModelRepository
import eu.transittrack.predict.model.PredictionModelRow
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest
import eu.transittrack.support.insertAvlFeed
import eu.transittrack.support.insertGtfsFeed
import eu.transittrack.support.insertGtfsRevision
import eu.transittrack.util.observability.TransitTrackMetrics

@PostgresSliceTest
class SmileTrainingJobTest(
    @Autowired val db: Database,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val models: PredictionModelRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val travelTimes: TravelTimesForStopPathRepository,
    @Autowired val agencies: AgencyRepository,
) : PostgresPerMethodTest() {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val props =
        PredictProperties(smile = PredictProperties.Smile(minTrainingRows = 100, maxTrainingRows = 1_000, trees = 5, maxDepth = 2))

    private lateinit var store: SmileModelStore
    private var seededPattern = 0L
    private var unseededPattern = 0L

    @BeforeEach
    fun seed() {
        store = SmileModelStore(models, clock)
        db.insertGtfsFeed(1, "g")
        db.insertGtfsRevision(1, 1)
        db.insertAvlFeed(1, FEED, gtfsFeedCode = "g")
        seededPattern = patterns.save(TripPattern(REV, "K1", "R1", null, 0, "H", "S1", 6, 1_000.0, tripCount = 1)).id!!
        unseededPattern = patterns.save(TripPattern(REV, "K2", "R2", null, 1, "H", "S2", 6, 1_000.0, tripCount = 1)).id!!
        for (i in 0..5) travelTimes.save(TravelTimesForStopPath(REV, seededPattern, i, if (i == 0) 0 else 60 + 10 * i, 20))
    }

    private fun job(trainer: SmileTrainer = SmileTrainer(props.smile)) =
        SmileTrainingJob(
            feeds = feeds,
            crossings = crossings,
            models = models,
            travelTimes = CachedTravelTimesReader(travelTimes),
            agencies = CachedAgencyReader(agencies),
            store = store,
            props = props,
            metrics = TransitTrackMetrics.forTests(),
            clock = clock,
            trainer = trainer,
        )

    private fun enableSmile() {
        val feed = feeds.findByCode(FEED)!!
        feed.predictionAlgorithms = listOf(PredictionAlgorithm.KALMAN, PredictionAlgorithm.SMILE_GBM)
        feeds.save(feed)
    }

    /** [count] crossings on the seeded pattern, plus [unseeded] on a pattern with no schedule seed. */
    private fun insertCrossings(
        count: Int,
        unseeded: Int = 0,
    ) {
        val rng = Random(11)
        val rows =
            (0 until count + unseeded).map { i ->
                val k = 1 + i % 5
                AvlStopCrossing(
                    feedCode = FEED,
                    vehicleId = "v${i % 7}",
                    tripRowId = i / 5L,
                    tripPatternId = if (i < count) seededPattern else unseededPattern,
                    stopPathIndex = k,
                    observedAt = now.minusSeconds(600L * (i + 1)),
                    observedTravelTimeSec = 60.0 + 10 * k + rng.nextGaussian() * 8,
                    serviceId = "WK",
                    tripStartSec = 6 * 3600 + (i / 5) * 600,
                    routeId = if (i < count) "R1" else "R2",
                    directionId = 0,
                    revisionId = REV,
                    reportGapSec = 10,
                )
            }
        crossings.saveAll(rows)
    }

    @Test
    fun trainFeedStoresAndPublishesModel() {
        enableSmile()
        insertCrossings(count = 150, unseeded = 30)

        job().trainFeed(FEED)

        val stored = models.find(FEED, PredictionAlgorithm.SMILE_GBM)
        assertThat(stored).isNotNull()
        assertThat(stored!!.trainedAt).isEqualTo(now)
        // The unseeded pattern's crossings are skipped.
        assertThat(stored.sampleCount).isEqualTo(150)

        val published = store.current(FEED)
        assertThat(published).isNotNull()
        assertThat(published!!.routeIndex).isEqualTo(mapOf("R1" to 0))
        // The published instance is the one the stored bytes restore to.
        val fromStored = StopToStopModel.fromBytes(stored.payload)
        val features = StopToStopFeatures.vector(3, 90.0, DayType.WEEKDAY, TimeBand.MORNING_PEAK, 25_200, 0, 0)
        assertThat(published.estimate(features)).isEqualTo(fromStored.estimate(features))
    }

    @Test
    fun feedWithoutSmileAlgorithmIsSkipped() {
        insertCrossings(count = 150)

        job().trainFeed(FEED)

        assertThat(models.find(FEED, PredictionAlgorithm.SMILE_GBM)).isNull()
        assertThat(store.current(FEED)).isNull()
    }

    @Test
    fun belowMinimumKeepsPreviousModel() {
        enableSmile()
        insertCrossings(count = 99)
        store.put(FEED, modelA, now.minusSeconds(86_400))

        job().trainFeed(FEED)

        assertThat(store.current(FEED)).isSameInstanceAs(modelA)
        assertThat(models.find(FEED, PredictionAlgorithm.SMILE_GBM)).isNull()
    }

    @Test
    fun failedTrainingKeepsPreviousModel() {
        enableSmile()
        insertCrossings(count = 150)
        store.put(FEED, modelA, now.minusSeconds(86_400))
        val failing = mock<SmileTrainer> { on { train(any(), any()) } doThrow IllegalStateException("boom") }

        job(failing).trainFeed(FEED)

        assertThat(store.current(FEED)).isSameInstanceAs(modelA)
        assertThat(models.find(FEED, PredictionAlgorithm.SMILE_GBM)).isNull()
    }

    @Test
    fun refreshLoadsNewerStoredModel() {
        store.put(FEED, modelA, now.minusSeconds(3_600))
        models.save(PredictionModelRow(FEED, PredictionAlgorithm.SMILE_GBM, now, 200, modelB.toBytes()))

        store.refresh()

        assertThat(store.current(FEED)!!.routeIndex).isEqualTo(modelB.routeIndex)

        // An older stored model never replaces a newer one in memory.
        store.put(FEED, modelA, now.plusSeconds(60))
        store.refresh()
        assertThat(store.current(FEED)).isSameInstanceAs(modelA)
    }

    @Test
    fun refreshIgnoresCorruptPayload() {
        store.put(FEED, modelA, now.minusSeconds(3_600))
        models.save(PredictionModelRow(FEED, PredictionAlgorithm.SMILE_GBM, now, 200, byteArrayOf(1, 2, 3)))

        store.refresh()

        assertThat(store.current(FEED)).isSameInstanceAs(modelA)
    }

    @Test
    fun readersNeverSeeAPartialModel() {
        store.put(FEED, modelA)
        val stop = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val writer =
            thread {
                started.countDown()
                var flip = false
                while (!stop.get()) {
                    store.put(FEED, if (flip) modelA else modelB)
                    flip = !flip
                }
            }
        started.await()
        val features = StopToStopFeatures.vector(2, 80.0, DayType.WEEKDAY, TimeBand.MIDDAY, 36_000, 0, 0)
        var allComplete = true
        try {
            repeat(10_000) {
                val m = store.current(FEED)
                if (m !== modelA && m !== modelB) allComplete = false
                m?.estimate(features)
            }
        } finally {
            stop.set(true)
            writer.join()
        }
        assertThat(allComplete).isTrue()
    }

    companion object {
        private const val FEED = "f1"
        private const val REV = 1L

        private val smallProps = PredictProperties.Smile(minTrainingRows = 100, trees = 5, maxDepth = 2)

        private fun syntheticModel(
            routeId: String,
            seed: Long,
        ): StopToStopModel {
            val rng = Random(seed)
            val samples =
                (0 until 300).map {
                    val k = 1 + rng.nextInt(10)
                    val seedSec = 40.0 + rng.nextInt(120)
                    TrainingSample(
                        StopToStopFeatures.vector(k, seedSec, DayType.WEEKDAY, TimeBand.MIDDAY, 30_000, 0, 0),
                        seedSec * (1.0 + rng.nextGaussian() * 0.1),
                    )
                }
            return SmileTrainer(smallProps).train(samples, mapOf(routeId to 0))!!
        }

        private val modelA = syntheticModel("A", 1)
        private val modelB = syntheticModel("B", 2)
    }
}
