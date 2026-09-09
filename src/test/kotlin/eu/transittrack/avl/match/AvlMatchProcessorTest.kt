package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
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
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.Point
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
    ],
)
@Import(TestcontainersConfiguration::class, AvlMatchProcessorTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvlMatchProcessorTest(
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
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
        vehicleMatches.deleteAll()
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
    fun `a batch matches only the newest report per vehicle and state reflects it`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM

        insertReport("bus-1", geom.line.pointAt(0.25 * len), ts(8, 10))
        insertReport("bus-1", geom.line.pointAt(0.60 * len), ts(8, 12))

        assertThat(processor.processBatch()).isEqualTo(2)

        // only the newest report is a real match attempt; the earlier one is skipped
        assertThat(reports.findAll().map { it.matchStatus }.toSet())
            .isEqualTo(setOf(MatchStatus.SKIPPED, MatchStatus.MATCHED))

        val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-1")!!
        assertThat(state.matched).isTrue()
        assertThat(state.consecutiveFailures).isEqualTo(0)
        assertThat(state.tripRowId).isEqualTo(trip.id)
        assertThat(state.distanceAlongTripM!!).isCloseTo(0.60 * len, 0.1 * len)

        assertThat(vehicleMatches.findByFeedIdAndVehicleIdOrderByTsDesc(feedId, "bus-1", Pageable.unpaged())).hasSize(1)
    }

    @Test
    fun `a garbage report fails the newest attempt and never matches the vehicle`() {
        val (_, geom) = t1Geom()
        val mid = geom.line.pointAt(0.5 * geom.line.lengthM)
        val garbage = Point(mid.lat + 0.05, mid.lon + 0.05)

        insertReport("bus-2", garbage, ts(8, 10))
        insertReport("bus-2", garbage, ts(8, 12))
        insertReport("bus-2", garbage, ts(8, 14))

        processor.processBatch()

        assertThat(reports.findAll().map { it.matchStatus }.toSet())
            .isEqualTo(setOf(MatchStatus.SKIPPED, MatchStatus.UNMATCHED))

        val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-2")!!
        // a vehicle that never had a trip assignment stays unmatched from the first failure
        assertThat(state.matched).isFalse()
        assertThat(state.stale).isTrue()
        assertThat(state.consecutiveFailures).isEqualTo(1)
        assertThat(state.tripRowId).isNull()
    }

    @Test
    fun `a matched vehicle keeps its trip through failed cycles until unmatchAfterFailures`() {
        val (trip, geom) = t1Geom()
        val len = geom.line.lengthM
        val mid = geom.line.pointAt(0.5 * len)
        val garbage = Point(mid.lat + 0.05, mid.lon + 0.05)

        insertReport("bus-3", geom.line.pointAt(0.3 * len), ts(8, 0))
        processor.processBatch()
        assertThat(vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-3")!!.matched).isTrue()

        // unmatchAfterFailures = 5: cycles 1..4 retain the trip (stale), cycle 5 drops it
        for (cycle in 1..5) {
            insertReport("bus-3", garbage, ts(8, cycle * 2))
            processor.processBatch()
            val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-3")!!
            assertThat(state.stale).isTrue()
            assertThat(state.consecutiveFailures).isEqualTo(cycle)
            if (cycle < 5) {
                assertThat(state.matched).isFalse()
                assertThat(state.tripRowId).isEqualTo(trip.id)
            } else {
                assertThat(state.matched).isFalse()
                assertThat(state.tripRowId).isNull()
            }
        }
    }
}
