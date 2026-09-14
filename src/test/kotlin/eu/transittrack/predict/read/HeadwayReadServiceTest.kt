package eu.transittrack.predict.read

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import org.junit.jupiter.api.AfterAll
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
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerClassTest

/**
 * `schedule-sample` facts used here: trips T1 and T2 are both route RA / direction 0 / service WK,
 * and share the same shape (SHP_OUT) with the identical stop sequence S1,S2,S3,S4 - so they derive
 * to the same TripPattern and both serve stop S3 as stopPathIndex 2. Stop S1 (index 0, the very
 * first stop of that pattern) is deliberately NOT used here: a vehicle positioned anywhere along
 * the shape - even a couple of percent in - is matched to stopPathIndex 1 already (the horizon
 * starts at the *next* unvisited stop), so the trip's own first stop never gets a prediction.
 * Route RA also has T3 serving S3 in direction 0 (on its own, shorter pattern S1,S3,S4), which is
 * what lets the scheduled-headway average be computed from >= 2 trips.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(HeadwayReadServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HeadwayReadServiceTest(
    @Autowired val headwayReadService: HeadwayReadService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val predictionProcessor: eu.transittrack.predict.PredictionProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L

    @BeforeAll
    fun setup() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "a", name = "A", gtfsFeedCode = "g", url = "x",
                        format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
                        assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                    ),
                ).id!!

        val feed = feeds.findById(feedId).orElseThrow()
        val ctx = factory.open(feed)!!

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
            vehicleId: String,
            tripId: String,
            p: Point,
            at: Instant,
        ) {
            reports.save(
                AvlReportRow(
                    feedId = feedId, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                    lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                    descTripId = tripId, descRouteId = "RA", descDirectionId = 0,
                    descStartDate = LocalDate.of(2026, 9, 7), descStartTimeSec = null,
                    descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                    currentStatus = null, occupancyStatus = null, congestionLevel = null,
                    matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                ),
            )
        }

        val t1 = trips.findByTripId(ctx.revisionId, "T1")!!
        val t2 = trips.findByTripId(ctx.revisionId, "T2")!!
        val t1Geom = ctx.patternGeometry(t1.tripPatternId!!)!!
        val t2Geom = ctx.patternGeometry(t2.tripPatternId!!)!!

        insert("bus-1", "T1", t1Geom.line.pointAt(0.02 * t1Geom.line.lengthM), ts(7, 55))
        insert("bus-2", "T2", t2Geom.line.pointAt(0.02 * t2Geom.line.lengthM), ts(8, 57))

        processor.processBatch()
        predictionProcessor.processBatch()
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
    fun `headway reports one gap between two matched vehicles and a scheduled baseline`() {
        val headway = headwayReadService.headway(feedCode = "a", stopId = "S3", routeId = "RA", directionId = 0)

        assertThat(headway.gapsSec).hasSize(1)
        assertThat(headway.gapsSec.first()).isGreaterThanOrEqualTo(0)
        assertThat(headway.waitSec).isNotNull()
        assertThat(headway.waitSec!!).isGreaterThanOrEqualTo(0)
        assertThat(headway.scheduledHeadwaySec).isNotNull()
    }
}
