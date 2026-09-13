package eu.transittrack.predict

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.Point
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.ingest.AvlIngestService
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
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository

/**
 * Full AVL + prediction pipeline through a real HTTP endpoint: a GTFS-RT `VehiclePosition` feed
 * served by [MockWebServer] is polled by [AvlIngestService], matched by [AvlMatchProcessor] against
 * the derived schedule model (`schedule-sample`), and the resulting [PredictionService] output -
 * predictions, filled-in actuals, accuracy samples, and read-time headway - is read back over
 * GraphQL. Mirrors the poll/match structure of `eu.transittrack.avl.AvlEndToEndTest`, asserting on
 * predictions instead of just match state.
 *
 * The first poll is deliberately positioned at 25% along the trip's shape, not at its very start:
 * stop-path index 0 is currently unreachable by the matcher for any trip pattern (the horizon
 * starts at the *next* unvisited stop), so a position right at the first stop would never produce
 * a prediction for it. See `docs/predictions.md` "Known limitations".
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(TestcontainersConfiguration::class, PredictionEndToEndTest.Stub::class)
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
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private lateinit var server: MockWebServer

    @BeforeAll
    fun setup() {
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
        predictionAccuracies.deleteAll()
        vehiclePredictions.deleteAll()
        travelTimeObservations.deleteAll()
        kalmanStates.deleteAll()
        vehicleMatches.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
        feeds.deleteAll()
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
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p25, svcTs(5).epochSecond))))
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
                    """{ predictionAccuracy(feedCode:"mbta-vp", algorithm:"SCHEDULE_ADHERENCE") {
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
}
