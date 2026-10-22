package eu.transittrack.reports.read

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

/**
 * `schedule-edge` facts used here: route RA has trips E1/E2/EF (S1 -> S4, stopPathIndex 0/1), and
 * route RB has trip E3 (S1 -> S3, stopPathIndex 0/1). Only RA's E1/E2 get seeded [VehicleMatch]
 * rows below; RB's E3 and RA's EF are left with none, so they exercise the
 * zero-sample-exclusion behavior that every summary method must apply.
 *
 * - E1 (bus-e1): reaches both of its scheduled stops - adherenceSec 30 then -30.
 * - E2 (bus-e2): reaches only its first scheduled stop - adherenceSec 120; its second stop stays
 *   unreached (`null` adherenceSec), which must be excluded from aggregation, not counted as 0.
 * - E3 (RB), EF (RA): no [VehicleMatch] rows at all - must not appear in any summary.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = ["transittrack.avl.enabled=true"],
)
@Import(AdherenceReadServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdherenceReadServiceTest(
    @Autowired val service: AdherenceReadService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-edge")
    }

    private var feedId = 0L
    private val serviceDate: LocalDate = LocalDate.of(2026, 9, 7)
    private val startDate: String = serviceDate.toString()
    private val endDate: String = serviceDate.toString()

    @BeforeAll
    fun setup() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "g", name = "G", gtfsFeedCode = "g", url = "x",
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
        ): Instant = serviceDate.atTime(hour, minute).atZone(ctx.zone).toInstant()

        fun avlReport(
            vehicleId: String,
            at: Instant,
            tripId: String,
            routeId: String,
        ): Long =
            reports
                .save(
                    AvlReportRow(
                        feedCode = feed.code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                        lat = 51.1, lon = 17.0, bearing = null, speedMps = null, odometerM = null,
                        descTripId = tripId, descRouteId = routeId, descDirectionId = 0,
                        descStartDate = serviceDate, descStartTimeSec = null,
                        descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                        currentStatus = null, occupancyStatus = null, congestionLevel = null,
                        matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                    ),
                ).id!!

        fun insertMatch(
            vehicleId: String,
            tripId: String,
            routeId: String,
            at: Instant,
            stopPathIndex: Int,
            adherenceSec: Int?,
        ) {
            val tripRow = trips.findByTripId(ctx.revisionId, tripId)!!
            val reportId = avlReport(vehicleId, at, tripId, routeId)
            vehicleMatches.save(
                VehicleMatch(
                    avlReportId = reportId, feedCode = feed.code, vehicleId = vehicleId, ts = at,
                    revisionId = ctx.revisionId, tripRowId = tripRow.id!!, blockPk = null,
                    tripPatternId = tripRow.tripPatternId, stopPathIndex = stopPathIndex,
                    distanceAlongTripM = 0.0, deviationM = 0.0, scheduleAdherenceSec = adherenceSec,
                    snappedLat = 51.1, snappedLon = 17.0, heading = null, score = null,
                    createdAt = at,
                ),
            )
        }

        // E1 (route RA): reaches both scheduled stops. A VehicleMatch's reported stopPathIndex
        // means "already arrived at stop (index - 1)" (see StopPassageDerivationServiceTest's KDoc
        // for the full semantics), so arrival at scheduled stop 0/1 is represented by stopPathIndex
        // 1/2, not 0/1.
        insertMatch("bus-e1", "E1", "RA", ts(8, 0), 1, 30)
        insertMatch("bus-e1", "E1", "RA", ts(8, 30), 2, -30)

        // E2 (route RA): reaches only its first scheduled stop.
        insertMatch("bus-e2", "E2", "RA", ts(9, 0), 1, 120)

        // E3 (route RB), EF (route RA): deliberately left with zero VehicleMatch rows.
    }

    @AfterAll
    fun tearDown() {
        vehicleMatches.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @Test
    fun `routeAdherence rejects an inverted date range`() {
        assertFailure { service.routeAdherence("g", "RA", "2026-01-10", "2026-01-05") }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `routeAdherence returns raw stop passages for the route`() {
        val passages = service.routeAdherence("g", "RA", startDate, endDate)

        assertThat(passages.map { it.adherenceSec }.filterNotNull().sorted()).isEqualTo(listOf(-30, 30, 120))
    }

    @Test
    fun `tripAdherence throws for an unknown trip id`() {
        assertFailure { service.tripAdherence("g", "no-such-trip", startDate, endDate) }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `tripAdherence returns raw stop passages for the trip`() {
        val passages = service.tripAdherence("g", "E1", startDate, endDate)

        assertThat(passages.map { it.adherenceSec }).isEqualTo(listOf(30, -30))
    }

    @Test
    fun `stopAdherence returns raw stop passages for the stop across routes and trips`() {
        val passages = service.stopAdherence("g", "S1", startDate, endDate)

        // S1 is stopPathIndex 0 for both E1 and E2.
        assertThat(passages.map { it.adherenceSec }.sortedBy { it }).isEqualTo(listOf(30, 120))
    }

    @Test
    fun `allRoutesAdherence excludes routes with zero samples and never divides by zero`() {
        val summaries = service.allRoutesAdherence("g", startDate, endDate)

        assertThat(summaries.map { it.routeId }).containsExactly("RA")
    }

    @Test
    fun `allRoutesAdherence computes mean adherence and on-time pct from stop passages`() {
        val summary = service.allRoutesAdherence("g", startDate, endDate).single { it.routeId == "RA" }

        assertThat(summary.sampleCount).isEqualTo(3)
        assertThat(summary.meanAdherenceSec).isEqualTo(40.0) // (30 - 30 + 120) / 3
        assertThat(summary.onTimePct).isEqualTo(100.0 * 2 / 3) // |30|,|30| <= 60; |120| > 60
        assertThat(summary.routeShortName).isEqualTo("A")
    }

    @Test
    fun `allTripsAdherence excludes trip executions with zero samples`() {
        val summaries = service.allTripsAdherence("g", null, startDate, endDate)

        assertThat(summaries.map { it.tripId }).containsExactly("E1", "E2")
    }

    @Test
    fun `allTripsAdherence computes per-trip-execution mean adherence and on-time pct`() {
        val summaries = service.allTripsAdherence("g", null, startDate, endDate)

        val e1 = summaries.single { it.tripId == "E1" }
        assertThat(e1.routeId).isEqualTo("RA")
        assertThat(e1.serviceDate).isEqualTo(serviceDate)
        assertThat(e1.sampleCount).isEqualTo(2)
        assertThat(e1.meanAdherenceSec).isEqualTo(0.0) // (30 - 30) / 2
        assertThat(e1.onTimePct).isEqualTo(100.0)

        val e2 = summaries.single { it.tripId == "E2" }
        assertThat(e2.sampleCount).isEqualTo(1)
        assertThat(e2.meanAdherenceSec).isEqualTo(120.0)
        assertThat(e2.onTimePct).isEqualTo(0.0)
    }

    @Test
    fun `allTripsAdherence scoped to a route excludes trips from other routes and zero-sample trips`() {
        val summaries = service.allTripsAdherence("g", "RB", startDate, endDate)

        assertThat(summaries).isEmpty()
    }
}
