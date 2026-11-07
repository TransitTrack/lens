package eu.transittrack.predict

import java.time.Clock
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
import eu.transittrack.geo.Point
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
import eu.transittrack.support.PostgresPerClassTest
import eu.transittrack.support.RestGet

/**
 * Full AVL + prediction pipeline through a real HTTP endpoint: a GTFS-RT `VehiclePosition` feed
 * served by [MockWebServer] is polled by [AvlIngestService], matched by [AvlMatchProcessor] against
 * the derived schedule model (`schedule-sample`), and the resulting [PredictionService] output -
 * predictions, filled-in actuals, accuracy samples, and read-time headway - is read back over
 * REST. Mirrors the poll/match structure of `eu.transittrack.avl.AvlEndToEndTest`, asserting on
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionEndToEndTest(
    @LocalServerPort val port: Int,
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
    fun `poll, match, cross a stop, then read predictions, accuracy and headway over REST`() {
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

        val get = RestGet(port)
        val predictionsAfterPoll1 = get("/api/v1/avl-feeds/mbta-vp/vehicles/bus-1/predictions").jsonArray.map { it.jsonObject }

        assertThat(predictionsAfterPoll1).isNotEmpty()
        assertThat(predictionsAfterPoll1.map { it.getValue("algorithm").jsonPrimitive.content }.toSet())
            .isEqualTo(setOf("SCHEDULE_ADHERENCE"))

        // --- poll 2: advanced to 60% along T1, later ts -> crosses a stop ---
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p60, svcTs(12).epochSecond))))
        whenever(clock.instant()).thenReturn(svcTs(12))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        processor.processBatch()
        predictionProcessor.processBatch()

        val predictionsAfterPoll2 = get("/api/v1/avl-feeds/mbta-vp/vehicles/bus-1/predictions").jsonArray.map { it.jsonObject }

        assertThat(predictionsAfterPoll2.any { it["actualArrival"].let { v -> v != null && v !is JsonNull } }).isTrue()

        val accuracy = get("/api/v1/avl-feeds/mbta-vp/prediction-accuracy?algorithm=SCHEDULE_ADHERENCE").jsonArray.map { it.jsonObject }

        assertThat(accuracy).isNotEmpty()
        val sampleCount = accuracy
            .first()
            .getValue("sampleCount")
            .jsonPrimitive.int
        assertThat(sampleCount).isGreaterThanOrEqualTo(1)

        // --- headway: single matched vehicle on route RA at stop S4 (T1's final stop) ---
        // S4 (not S3) on purpose: by poll 2 the vehicle (60% along) has already crossed into S3's
        // stop path, so S3's vehicle_prediction row now carries both predicted_arrival_ts AND
        // actual_arrival_ts (crossing detection fills the same row) and is excluded from
        // stopPredictions' forward-looking filter - see HeadwayReadService/PredictionReadService.
        // S4 is still purely in the future and stays eligible.
        val headway = get("/api/v1/avl-feeds/mbta-vp/stops/S4/headway?routeId=RA&directionId=0").jsonObject

        assertThat(headway["waitSec"]).isNotNull()
        assertThat(headway.getValue("gapsSec").jsonArray).isEmpty()
    }
}
