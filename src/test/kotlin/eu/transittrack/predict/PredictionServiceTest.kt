package eu.transittrack.predict

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
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

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.Point
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
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.learn.TravelTimeBucket
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.avl.match.claim-batch-size=2000",
        "transittrack.feed.feeds=",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(PredictionServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionServiceTest(
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val predictionProcessor: eu.transittrack.predict.PredictionProcessor,
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
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val tripPatterns: TripPatternRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
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
        crossings.deleteAll()
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
            .now(factory.open(feedRow())!!.zone)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
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
                feedCode = feedRow().code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                descTripId = null, descRouteId = null, descDirectionId = null,
                descStartDate = at.atZone(factory.open(feedRow())!!.zone).toLocalDate(), descStartTimeSec = null,
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
        predictionProcessor.processBatch()

        val preds = vehiclePredictions.findByVehicleTrip(feedRow().code, "bus-1", trip.id!!)
        assertThat(preds).isNotEmpty()
        assertThat(preds.map { it.algorithm }.toSet()).isEqualTo(setOf(PredictionAlgorithm.SCHEDULE_ADHERENCE))
        preds.forEach { assertThat(it.actualArrivalTs).isNull() }
    }

    @Test
    fun `passing stops lands actuals on the stop reached and learns stop-to-stop times`() {
        val (trip, geom) = t1Geom()
        val cum = geom.stopPathCumM
        val len = geom.line.lengthM
        val patternId = trip.tripPatternId!!

        // Stop k sits at the end of stop path k: stop 1 at cum[2], stop 2 at cum[3], stop 3 at len.
        fun advanceTo(
            alongM: Double,
            at: Instant,
        ) {
            insertReport("bus-1", geom.line.pointAt(alongM), at)
            processor.processBatch()
            predictionProcessor.processBatch()
        }

        advanceTo((cum[1] + cum[2]) / 2, ts(8, 5)) // on stop path 1, heading for stop 1
        advanceTo((cum[2] + cum[3]) / 2, ts(8, 15)) // passed stop 1

        val afterStop1 = vehiclePredictions.findByVehicleTrip(feedRow().code, "bus-1", trip.id!!).associateBy { it.stopPathIndex }
        assertThat(afterStop1.getValue(1).actualArrivalTs != null).isTrue()
        assertThat(afterStop1.getValue(2).actualArrivalTs).isNull()

        advanceTo((cum[3] + len) / 2, ts(8, 25)) // passed stop 2

        val passes = crossings.findAll().filter { it.tripRowId == trip.id }.sortedBy { it.stopPathIndex }
        assertThat(passes.map { it.stopPathIndex }).isEqualTo(listOf(1, 2))
        assertThat(passes.all { it.feedCode == feedRow().code && it.tripPatternId == patternId }).isTrue()
        // Stop 1's pass has no earlier pass to measure from; stop 2's is timed from stop 1's -
        // roughly the scheduled 10 minutes, not the gap between two AVL reports.
        assertThat(passes[0].observedTravelTimeSec).isNull()
        assertThat(passes[1].observedTravelTimeSec!! > 300.0).isTrue()

        // One sample - stop path 2, driven from ~08:10 on a Monday - lands in its weekday morning-peak
        // bucket and in both roll-ups, keyed by the pattern's stable key rather than its row id.
        val patternKey = tripPatterns.findById(patternId).orElseThrow().patternKey
        val learned = travelTimeObservations.findAll()
        assertThat(learned.all { it.gtfsFeedCode == "g" && it.patternKey == patternKey && it.stopPathIndex == 2 }).isTrue()
        assertThat(learned.map { TravelTimeBucket(it.dayType, it.timeBand) }.toSet()).isEqualTo(
            setOf(
                TravelTimeBucket(DayType.WEEKDAY, TimeBand.MORNING_PEAK),
                TravelTimeBucket(DayType.WEEKDAY, TimeBand.ALL),
                TravelTimeBucket.OVERALL,
            ),
        )
        assertThat(predictionAccuracies.findAll().any { it.tripRowId == trip.id && it.stopPathIndex == 1 }).isTrue()
    }

    @Test
    fun `listing all three algorithms runs all three`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM

        val feed = feedRow()
        feed.predictionAlgorithms = PredictionAlgorithm.entries
        feeds.save(feed)

        insertReport("bus-2", geom.line.pointAt(0.25 * len), ts(8, 5))

        processor.processBatch()
        predictionProcessor.processBatch()

        val preds = vehiclePredictions.findByVehicleTrip(feedRow().code, "bus-2", trip.id!!)
        assertThat(preds).isNotEmpty()
        val algorithms = preds.map { it.algorithm }.toSet()
        assertThat(algorithms).contains(PredictionAlgorithm.SCHEDULE_ADHERENCE)
        assertThat(algorithms).contains(PredictionAlgorithm.HISTORICAL_AVERAGE)
        assertThat(algorithms).contains(PredictionAlgorithm.KALMAN)
        assertThat(preds.size % 3).isEqualTo(0)
        assertThat(algorithms).isEqualTo(PredictionAlgorithm.entries.toSet())
    }
}
