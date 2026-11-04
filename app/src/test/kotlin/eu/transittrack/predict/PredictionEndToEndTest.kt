package eu.transittrack.predict

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Point
import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.ingest.AvlIngestService
import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlMatchProcessor
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.predict.smile.SmileModelStore
import eu.transittrack.predict.smile.SmileTrainer
import eu.transittrack.predict.smile.StopToStopFeatures
import eu.transittrack.predict.smile.StopToStopModel
import eu.transittrack.predict.smile.TrainingSample
import eu.transittrack.support.PostgresPerClassTest

/**
 * Full AVL + prediction pipeline through a real HTTP endpoint: a GTFS-RT `VehiclePosition` feed
 * served by [MockWebServer] is polled by [AvlIngestService], matched by [AvlMatchProcessor] against
 * the derived schedule model (`schedule-sample`), and the resulting [PredictionService] output -
 * predictions, filled-in actuals, accuracy samples, and read-time headway - is read back over
 * GraphQL. Mirrors the poll/match structure of `eu.transittrack.avl.AvlEndToEndTest`, asserting on
 * predictions instead of just match state.
 *
 * The first poll is positioned at 25% along the shape so the next report crosses an intermediate
 * stop within the default reliable GPS interval.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        // Drives reports through the real HTTP source, so their fetchedAt is real wall-clock time,
        // while the fixture's own vehicle positions carry a fixed 2026-09-07 GTFS service date -
        // exempt this test from the max-report-age staleness filter rather than the other way round.
        "transittrack.avl.ingest.max-report-age-hours=87600",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(PredictionEndToEndTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionEndToEndTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val ingestService: AvlIngestService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val predictionProcessor: eu.transittrack.predict.PredictionProcessor,
    @Autowired val contextFactory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val smileModels: SmileModelStore,
    @Autowired val exposedDatabase: Database,
) : PostgresPerClassTest() {
    @MockitoBean
    lateinit var clock: Clock

    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private lateinit var server: MockWebServer

    @BeforeAll
    fun setup() {
        truncateBeforeFixture()
        server = MockWebServer()
        server.start()

        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feeds.save(
            AvlFeed(
                code = "mbta-vp", name = "MBTA", gtfsFeedCode = "g",
                url = server.url("/vp.pb").toString(), format = AvlFormat.GTFS_RT,
                pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
                enabled = true, headers = null, source = AvlFeedSourceKind.CONFIG,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
    }

    @AfterAll
    fun cleanup() {
        server.shutdown()
        resetVehicleState()
        feeds.deleteAll()
    }

    /**
     * Forgets everything a driven vehicle left behind (reports, match and vehicle state, predictions,
     * snapshots, accuracy, learned travel times), so each test drives `bus-1` from a clean slate
     * whatever order the tests run in.
     */
    private fun resetVehicleState() {
        predictionAccuracies.deleteAll()
        transaction(exposedDatabase) {
            PredictionCheckpointTable.deleteAll()
            PredictionFrequencyRunTable.deleteAll()
        }
        crossings.deleteAll()
        vehiclePredictions.deleteAll()
        travelTimeObservations.deleteAll()
        kalmanStates.deleteAll()
        vehicleMatches.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
    }

    private fun feedMessageBytes(
        pt: Point,
        epochSec: Long,
    ): ByteArray =
        FeedMessage
            .newBuilder()
            .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(epochSec))
            .addEntity(
                FeedEntity
                    .newBuilder()
                    .setId("e")
                    .setVehicle(
                        VehiclePosition
                            .newBuilder()
                            .setVehicle(VehicleDescriptor.newBuilder().setId("bus-1"))
                            .setPosition(
                                Position
                                    .newBuilder()
                                    .setLatitude(pt.lat.toFloat())
                                    .setLongitude(pt.lon.toFloat()),
                            ).setTimestamp(epochSec),
                    ),
            ).build()
            .toByteArray()

    @Test
    fun `poll, match, cross a stop, then read predictions, accuracy and headway over GraphQL`() {
        val feedRow = feeds.findByCode("mbta-vp")!!
        val ctx = contextFactory.open(feedRow)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val len = geom.line.lengthM
        val p25 = geom.line.pointAt(len * 0.25)
        val p60 = geom.line.pointAt(len * 0.60)

        val svcTs = { minute: Int ->
            LocalDate
                .of(2026, 9, 7)
                .atTime(8, minute)
                .atZone(ctx.zone)
                .toInstant()
        }

        // --- poll 1: on-shape at 25% along T1 ---
        // Not asserting processBatch()'s raw return count: it claims PENDING reports across every
        // enabled AVL feed (including the config-seeded live feed from application config), not
        // just the one seeded here - see PredictionServiceTest for the same caveat.
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p25, svcTs(10).epochSecond))))
        whenever(clock.instant()).thenReturn(svcTs(10))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        processor.processBatch()
        predictionProcessor.processBatch()

        val predictionsAfterPoll1 =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"mbta-vp", vehicleId:"bus-1") {
                         stopPathIndex predictedArrival algorithm actualArrival
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(predictionsAfterPoll1).isNotEmpty()
        assertThat(predictionsAfterPoll1.map { it["algorithm"] }.toSet()).isEqualTo(setOf("SCHEDULE_ADHERENCE"))

        // --- poll 2: advanced to 60% along T1, later ts -> crosses a stop ---
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p60, svcTs(12).epochSecond))))
        whenever(clock.instant()).thenReturn(svcTs(12))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        processor.processBatch()
        predictionProcessor.processBatch()

        val predictionsAfterPoll2 =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"mbta-vp", vehicleId:"bus-1") {
                         stopPathIndex predictedArrival algorithm actualArrival
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(predictionsAfterPoll2.any { it["actualArrival"] != null }).isTrue()

        val accuracy =
            tester
                .document(
                    """{ predictionAccuracy(feedCode:"mbta-vp", algorithm:SCHEDULE_ADHERENCE) {
                         algorithm sampleCount
                       } }""",
                ).execute()
                .path("predictionAccuracy")
                .entityList(Map::class.java)
                .get()

        assertThat(accuracy).isNotEmpty()
        val sampleCount = (accuracy.first()["sampleCount"] as Number).toInt()
        assertThat(sampleCount).isGreaterThanOrEqualTo(1)

        // --- headway: single matched vehicle on route RA at stop S4 (T1's final stop) ---
        // S4 (not S3) on purpose: by poll 2 the vehicle (60% along) has already crossed into S3's
        // stop path, so S3's vehicle_prediction row now carries both predicted_arrival_ts AND
        // actual_arrival_ts (crossing detection fills the same row) and is excluded from
        // stopPredictions' forward-looking filter - see HeadwayReadService/PredictionReadService.
        // S4 is still purely in the future and stays eligible.
        val headway =
            tester
                .document(
                    """{ headway(feedCode:"mbta-vp", stopId:"S4", routeId:"RA", directionId:0) {
                         waitSec gapsSec
                       } }""",
                ).execute()
                .path("headway")
                .entity(Map::class.java)
                .get()

        assertThat(headway["waitSec"]).isNotNull()
        @Suppress("UNCHECKED_CAST")
        assertThat(headway["gapsSec"] as List<Int>).isEmpty()
    }

    /**
     * SMILE_GBM in shadow: listed after KALMAN, it predicts and is scored (range included) on every
     * report, but a stop board without an explicit algorithm keeps serving KALMAN.
     *
     * The model is a real Smile model, trained here with [SmileTrainer] on synthetic samples shaped
     * like this fixture (route RA, 10-minute legs) and published straight to this context's
     * [SmileModelStore]: nothing is written to `prediction_model`, so no other cached context's
     * store refresh can pick it up. The nightly job that trains from `avl_stop_crossing` is covered
     * by `SmileTrainingJobTest`.
     */
    @Test
    fun smileRunsInShadowAndIsScoredWithoutBeingServed() {
        resetVehicleState()
        val feedRow = feeds.findByCode("mbta-vp")!!
        val configured = feedRow.predictionAlgorithms
        feedRow.predictionAlgorithms = listOf(PredictionAlgorithm.KALMAN, PredictionAlgorithm.SMILE_GBM)
        feeds.save(feedRow)
        try {
            smileModels.put(feedRow.code, syntheticModel(), Instant.parse("2026-09-07T01:30:00Z"))

            val ctx = contextFactory.open(feedRow)!!
            val trip = trips.findByTripId(ctx.revisionId, "T1")!!
            val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
            val len = geom.line.lengthM
            val svcTs = { minute: Int ->
                LocalDate
                    .of(2026, 9, 7)
                    .atTime(8, minute)
                    .atZone(ctx.zone)
                    .toInstant()
            }

            // T1 runs S1 08:00 -> S2 08:10 (1/3 along) -> S3 08:20 (2/3) -> S4 08:30. Reports every
            // two minutes (within the reliable report gap), roughly on schedule, crossing S2 and S3.
            val drive = listOf(6 to 0.20, 8 to 0.27, 10 to 0.36, 12 to 0.41, 14 to 0.47, 16 to 0.54, 18 to 0.60, 20 to 0.70)
            for ((minute, fraction) in drive) {
                server
                    .enqueue(
                        MockResponse()
                            .setBody(Buffer().write(feedMessageBytes(geom.line.pointAt(len * fraction), svcTs(minute).epochSecond))),
                    )
                whenever(clock.instant()).thenReturn(svcTs(minute))
                assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
                processor.processBatch()
                predictionProcessor.processBatch()
            }

            // Both algorithms predict the vehicle's trip; SMILE_GBM's rows carry a symmetric range.
            val vehicleRows =
                tester
                    .document(
                        """{ vehiclePredictions(feedCode:"mbta-vp", vehicleId:"bus-1") {
                             stopPathIndex algorithm predictedArrival actualArrival rangeLowSec rangeHighSec late
                           } }""",
                    ).execute()
                    .path("vehiclePredictions")
                    .entityList(Map::class.java)
                    .get()
            val algorithms = vehicleRows.mapNotNull { it["algorithm"] }.toSet()
            assertThat(algorithms).contains("KALMAN")
            assertThat(algorithms).contains("SMILE_GBM")
            val smileRanged = vehicleRows.filter { it["algorithm"] == "SMILE_GBM" && it["rangeHighSec"] != null }
            assertThat(smileRanged).isNotEmpty()
            assertThat(smileRanged.all { (it["rangeLowSec"] as Number).toInt() == -(it["rangeHighSec"] as Number).toInt() }).isTrue()

            // The stop board without an algorithm serves the first configured one: KALMAN, not SMILE_GBM.
            val board = { algorithm: String? ->
                tester
                    .document(
                        """{ stopPredictions(feedCode:"mbta-vp", stopId:"S4", routeId:"RA", directionId:0
                             ${algorithm?.let { ", algorithm:$it" } ?: ""}) { algorithm predictedArrival rangeHighSec } }""",
                    ).execute()
                    .path("stopPredictions")
                    .entityList(Map::class.java)
                    .get()
            }
            val served = board(null)
            assertThat(served).isNotEmpty()
            assertThat(served.map { it["algorithm"] }.toSet()).isEqualTo(setOf("KALMAN"))
            // Asked for explicitly, the shadow algorithm's board is there too.
            val shadow = board("SMILE_GBM")
            assertThat(shadow).isNotEmpty()
            assertThat(shadow.map { it["algorithm"] }.toSet()).isEqualTo(setOf("SMILE_GBM"))

            // Accuracy compares both configured algorithms; SMILE_GBM's forecasts were scored with their range.
            val accuracy =
                tester
                    .document(
                        """{ predictionAccuracy(feedCode:"mbta-vp") {
                             algorithm sampleCount rangeSampleCount rangeCoverage
                           } }""",
                    ).execute()
                    .path("predictionAccuracy")
                    .entityList(Map::class.java)
                    .get()
            assertThat(accuracy.map { it["algorithm"] }.toSet()).isEqualTo(setOf("KALMAN", "SMILE_GBM"))
            val smile = accuracy.single { it["algorithm"] == "SMILE_GBM" }
            assertThat((smile["sampleCount"] as Number).toInt()).isGreaterThan(0)
            assertThat((smile["rangeSampleCount"] as Number).toInt()).isGreaterThan(0)
            assertThat(smile["rangeCoverage"]).isNotNull()
            // KALMAN gives no range, so it has nothing to score one with.
            val kalman = accuracy.single { it["algorithm"] == "KALMAN" }
            assertThat((kalman["sampleCount"] as Number).toInt()).isGreaterThan(0)
            assertThat((kalman["rangeSampleCount"] as Number).toInt()).isEqualTo(0)
        } finally {
            feedRow.predictionAlgorithms = configured
            feeds.save(feedRow)
            resetVehicleState()
        }
    }

    /**
     * A small but real SMILE_GBM model for route RA: stop-to-stop time ~1.05x the schedule seed with
     * about 30 s of noise, over seeds around this fixture's 10-minute legs.
     */
    private fun syntheticModel(): StopToStopModel {
        val routeIndex = mapOf("RA" to 0)
        val rng = Random(7)
        val samples =
            List(600) {
                val seedSec = 300.0 + rng.nextDouble() * 600.0
                val features =
                    StopToStopFeatures.vector(
                        stopPathIndex = 1 + rng.nextInt(3),
                        seedSec = seedSec,
                        dayType = DayType.entries[rng.nextInt(DayType.entries.size)],
                        timeBand = TimeBand.entries[rng.nextInt(TimeBand.entries.size)],
                        tripStartSec = 6 * 3_600 + rng.nextInt(4 * 3_600),
                        directionId = rng.nextInt(2),
                        routeIndex = 0,
                    )
                TrainingSample(features, 1.05 * seedSec + (rng.nextDouble() - 0.5) * 60.0)
            }
        val props = PredictProperties.Smile(minTrainingRows = 100, maxTrainingRows = 1_000, trees = 30, maxDepth = 3)
        return requireNotNull(SmileTrainer(props).train(samples, routeIndex)) { "synthetic training returned null" }
    }
}
