package eu.transittrack.predict.api

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
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
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlMatchProcessor
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
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
import eu.transittrack.schedule.model.ScheduleTimeRepository

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
    ],
)
@Import(TestcontainersConfiguration::class, PredictionGraphQlTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L

    @BeforeAll
    fun setup() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "a", name = "A", gtfsFeedCode = "g", url = "x",
                        format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
                        assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                    ),
                ).id!!

        val feed = feeds.findById(feedId).orElseThrow()
        val ctx = factory.open(feed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val len = geom.line.lengthM

        fun ts(
            hour: Int,
            minute: Int,
        ): Instant =
            LocalDate
                .of(2026, 9, 7)
                .atTime(hour, minute)
                .atZone(ctx.zone)
                .toInstant()

        fun insert(
            p: Point,
            at: Instant,
        ) {
            reports.save(
                AvlReportRow(
                    feedId = feedId, vehicleId = "bus-1", vehicleLabel = null, ts = at,
                    lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                    descTripId = null, descRouteId = null, descDirectionId = null,
                    descStartDate = LocalDate.of(2026, 9, 7), descStartTimeSec = null,
                    descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                    currentStatus = null, occupancyStatus = null, congestionLevel = null,
                    matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                ),
            )
        }

        insert(geom.line.pointAt(0.25 * len), ts(8, 10))
        processor.processBatch()

        insert(geom.line.pointAt(0.60 * len), ts(8, 12))
        processor.processBatch()
    }

    @AfterAll
    fun tearDown() {
        predictionAccuracies.deleteAll()
        vehiclePredictions.deleteAll()
        travelTimeObservations.deleteAll()
        kalmanStates.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @Test
    fun `vehiclePredictions returns stop predictions with nested stop`() {
        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1") {
                         stopPathIndex predictedArrival algorithm stop { stopId }
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isNotEmpty()
        val first = rows.first()
        assertThat(first["algorithm"]).isEqualTo("SCHEDULE_ADHERENCE")
        @Suppress("UNCHECKED_CAST")
        assertThat((first["stop"] as Map<String, Any?>)["stopId"]).isNotNull()
    }

    @Test
    fun `vehiclePredictions schedules arrivals in the agency timezone`() {
        val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-1")!!
        val prediction =
            vehiclePredictions
                .findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feedId, "bus-1", state.tripRowId!!)
                .first()
        val schedule =
            scheduleTimes
                .findByTripOrdered(state.revisionId!!, state.tripRowId!!)
                .first { it.stopPathIndex == prediction.stopPathIndex }
        val expected =
            prediction
                .computedAt
                .atZone(ZoneId.of("Europe/Warsaw"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .plusSeconds(schedule.arrivalSec!!.toLong())
                .toInstant()
                .toString()

        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1") {
                         stopPathIndex scheduledArrival
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        val row = rows.first { (it["stopPathIndex"] as Number).toInt() == prediction.stopPathIndex }
        assertThat(row["scheduledArrival"]).isEqualTo(expected)
    }

    @Test
    fun `predictionAccuracy summarizes samples after a crossing`() {
        val rows =
            tester
                .document("""{ predictionAccuracy(feedCode:"a") { algorithm sampleCount } }""")
                .execute()
                .path("predictionAccuracy")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isNotEmpty()
        val sampleCount = (rows.first()["sampleCount"] as Number).toInt()
        assertThat(sampleCount).isGreaterThanOrEqualTo(1)
    }
}
