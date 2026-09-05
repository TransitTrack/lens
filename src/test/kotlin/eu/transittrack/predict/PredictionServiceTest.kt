package eu.transittrack.predict

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Point
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat
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

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
    ],
)
@Import(TestcontainersConfiguration::class, PredictionServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionServiceTest(
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    @org.junit.jupiter.api.BeforeEach
    fun seed() {
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
    }

    @AfterEach
    fun cleanup() {
        predictionAccuracies.deleteAll()
        vehiclePredictions.deleteAll()
        travelTimeObservations.deleteAll()
        kalmanStates.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    private fun ts(
        hour: Int,
        minute: Int,
    ): Instant =
        LocalDate
            .of(2026, 9, 7)
            .atTime(hour, minute)
            .atZone(factory.open(feedRow())!!.zone)
            .toInstant()

    private fun feedRow(): AvlFeed = feeds.findById(feedId).orElseThrow()

    private fun t1Geom() =
        factory.open(feedRow())!!.let { ctx ->
            val trip = trips.findByTripId(ctx.revisionId, "T1")!!
            trip to ctx.patternGeometry(trip.tripPatternId!!)!!
        }

    private fun insertReport(
        vehicleId: String,
        p: Point,
        at: Instant,
    ) {
        reports.save(
            AvlReportRow(
                feedId = feedId, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                descTripId = null, descRouteId = null, descDirectionId = null,
                descStartDate = LocalDate.of(2026, 9, 7), descStartTimeSec = null,
                descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                currentStatus = null, occupancyStatus = null, congestionLevel = null,
                matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
            ),
        )
    }

    @Test
    fun `first match generates predictions with the feed's default algorithm and no actuals`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM

        insertReport("bus-1", geom.line.pointAt(0.25 * len), ts(8, 5))

        // Not asserting the raw processed count: processBatch() claims PENDING reports across every
        // enabled AVL feed (including the config-seeded live feed), not just the one seeded here.
        processor.processBatch()

        val preds = vehiclePredictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feedId, "bus-1", trip.id!!)
        assertThat(preds).isNotEmpty()
        assertThat(preds.map { it.algorithm }.toSet()).isEqualTo(setOf(PredictionAlgorithm.SCHEDULE_ADHERENCE))
        preds.forEach { assertThat(it.actualArrivalTs).isNull() }
    }

    @Test
    fun `second advancing report crosses a stop, filling actuals and learning`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM

        insertReport("bus-1", geom.line.pointAt(0.25 * len), ts(8, 5))
        insertReport("bus-1", geom.line.pointAt(0.60 * len), ts(8, 12))

        processor.processBatch()

        val preds = vehiclePredictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feedId, "bus-1", trip.id!!)
        assertThat(preds.any { it.actualArrivalTs != null }).isTrue()

        val patternId = trip.tripPatternId!!
        assertThat(travelTimeObservations.findAll().any { it.tripPatternId == patternId }).isTrue()
        assertThat(predictionAccuracies.findAll().any { it.tripRowId == trip.id }).isTrue()
    }

    @Test
    fun `evaluation mode runs all three algorithms`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM

        val feed = feedRow()
        feed.predictionMode = PredictionMode.EVALUATION
        feeds.save(feed)

        insertReport("bus-2", geom.line.pointAt(0.25 * len), ts(8, 5))

        processor.processBatch()

        val preds = vehiclePredictions.findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(feedId, "bus-2", trip.id!!)
        assertThat(preds).isNotEmpty()
        val algorithms = preds.map { it.algorithm }.toSet()
        assertThat(algorithms).contains(PredictionAlgorithm.SCHEDULE_ADHERENCE)
        assertThat(algorithms).contains(PredictionAlgorithm.HISTORICAL_AVERAGE)
        assertThat(algorithms).contains(PredictionAlgorithm.KALMAN)
        assertThat(preds.size % 3).isEqualTo(0)
        assertThat(algorithms).isEqualTo(PredictionAlgorithm.entries.toSet())
    }
}
