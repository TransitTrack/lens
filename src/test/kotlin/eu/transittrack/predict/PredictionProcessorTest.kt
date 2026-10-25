package eu.transittrack.predict

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.Pageable
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
import eu.transittrack.avl.model.PredictionStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerClassTest
import eu.transittrack.util.concurrency.FeedLockCoordinator

/**
 * Covers the core scenario this decoupling plan exists to fix: multiple `vehicle_match` rows for the
 * SAME vehicle, all claimed together in one [PredictionProcessor] batch. A fixed-capacity executor used
 * to silently drop work like this under load; [PredictionProcessor] must drain every claimed row and
 * thread `prev` correctly across the batch so crossing detection fires between consecutive rows.
 */
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
@Import(PredictionProcessorTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionProcessorTest(
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val predictionProcessor: PredictionProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val lockProvider: JdbcTemplateLockProvider,
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
        // See AvlMatchProcessorTest/AvlPollerTest for why this is needed, and why it runs *after*
        // the truncate rather than before.
        truncateBeforeFixture()
        lockProvider.clearCache()
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
        vehicleMatches.deleteAll()
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
    ): Long =
        reports
            .save(
                AvlReportRow(
                    feedCode = feedRow().code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                    lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                    descTripId = null, descRouteId = null, descDirectionId = null,
                    descStartDate = at.atZone(factory.open(feedRow())!!.zone).toLocalDate(), descStartTimeSec = null,
                    descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                    currentStatus = null, occupancyStatus = null, congestionLevel = null,
                    matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                ),
            ).id!!

    @Test
    fun `processBatch drains every claimed row for a vehicle in one pass, threading prev across the batch`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM
        val patternId = trip.tripPatternId!!

        // Three advancing reports for ONE vehicle, all inserted (and matched) before PredictionProcessor
        // ever runs — this is exactly the backlog shape a fixed-capacity executor used to drop under load.
        insertReport("bus-1", geom.line.pointAt(0.20 * len), ts(8, 5))
        insertReport("bus-1", geom.line.pointAt(0.50 * len), ts(8, 9))
        insertReport("bus-1", geom.line.pointAt(0.80 * len), ts(8, 12))

        // One AvlMatchProcessor pass matches all three reports, producing three PENDING vehicle_match rows.
        processor.processBatch()

        val matchedBefore = vehicleMatches.findByVehicle(feedRow().code, "bus-1", Pageable.unpaged())
        assertThat(matchedBefore).hasSize(3)
        assertThat(matchedBefore.all { it.predictionStatus == PredictionStatus.PENDING }).isTrue()

        // One PredictionProcessor pass must claim and process the whole batch, not just the first row.
        predictionProcessor.processBatch()

        val matchedAfter = vehicleMatches.findByVehicle(feedRow().code, "bus-1", Pageable.unpaged())
        assertThat(matchedAfter).hasSize(3)
        assertThat(matchedAfter.all { it.predictionStatus == PredictionStatus.DONE }).isTrue()

        // Stop-pass detection must have fired between consecutive claimed rows (not just against stale
        // prior state) — this is what a broken prev-threading loop in processFeed would fail to produce.
        // Stop 1 is the first pass seen (no sample); stop 2's pass follows it, so it yields one.
        val observedCrossings =
            crossings.findAll().filter { it.feedCode == feedRow().code && it.tripPatternId == patternId }.sortedBy { it.stopPathIndex }
        assertThat(observedCrossings.map { it.stopPathIndex }).isEqualTo(listOf(1, 2))
        assertThat(observedCrossings[0].observedTravelTimeSec).isNull()
        assertThat(observedCrossings[1].observedTravelTimeSec).isNotNull()
        assertThat(travelTimeObservations.findAll().any { it.stopPathIndex == 2 }).isTrue()

        // Only the newest match of the backlog is worth predicting from: nothing behind it is left open.
        val preds = vehiclePredictions.findByVehicleTrip(feedRow().code, "bus-1", trip.id!!)
        assertThat(preds.map { it.stopPathIndex }.toSet()).isEqualTo(setOf(3))

        // The next report reaches the last stop: its actual lands on that stop, not the one after it.
        insertReport("bus-1", geom.line.pointAt(len), ts(8, 30))
        processor.processBatch()
        predictionProcessor.processBatch()
        val last = vehiclePredictions.findByVehicleTrip(feedRow().code, "bus-1", trip.id!!).single { it.stopPathIndex == 3 }
        assertThat(last.actualArrivalTs).isNotNull()
    }

    @Test
    fun `findClaimBatch claims the globally oldest rows first, not grouped by vehicle id string order`() {
        val (trip, _) = t1Geom()
        val revisionId = factory.open(feedRow())!!.revisionId

        // "1002" sorts before "547" lexicographically ('1' < '5'), even though its report is newer.
        // A claim-batch ordered by vehicle id first would return "1002"'s row here; ordering by ts
        // first must return "547"'s older row instead.
        val newerReportId = insertReport("1002", Point(0.0, 0.0), ts(9, 0))
        val olderReportId = insertReport("547", Point(0.0, 0.0), ts(8, 0))

        fun insertVehicleMatch(
            vehicleId: String,
            at: Instant,
            avlReportId: Long,
        ) = vehicleMatches.save(
            VehicleMatch(
                avlReportId = avlReportId, feedCode = feedRow().code, vehicleId = vehicleId, ts = at,
                revisionId = revisionId, tripRowId = trip.id!!, blockPk = null, tripPatternId = trip.tripPatternId,
                stopPathIndex = 0, distanceAlongTripM = 0.0, deviationM = 0.0, scheduleAdherenceSec = null,
                snappedLat = 0.0, snappedLon = 0.0, heading = null, score = null, createdAt = at,
            ),
        )

        insertVehicleMatch("1002", ts(9, 0), newerReportId)
        insertVehicleMatch("547", ts(8, 0), olderReportId)

        val claimed = vehicleMatches.findClaimBatch(feedRow().code, 1)
        assertThat(claimed).hasSize(1)
        assertThat(claimed.first().vehicleId).isEqualTo("547")
    }

    @Test
    fun `reconcile does not start a task for a feed already owned by another coordinator`() {
        // lockAtLeastFor is deliberately ~zero: a nonzero value keeps the lock held for that
        // minimum even across an explicit release()/unlock(), which is correct ShedLock behavior
        // but would make this test's second assertion (release frees it) flaky/wrong.
        val otherPod = FeedLockCoordinator(lockProvider, "predictor-feed", Duration.ofMinutes(3), Duration.ZERO)
        otherPod.reconcileOwnership(setOf(feedRow().code))

        predictionProcessor.reconcile()
        assertThat(predictionProcessor.runningFeedIds()).doesNotContain(feedRow().code)

        otherPod.release(feedRow().code)
        predictionProcessor.reconcile()
        assertThat(predictionProcessor.runningFeedIds()).contains(feedRow().code)
    }
}
