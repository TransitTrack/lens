package eu.transittrack.reports.api

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.support.PostgresPerClassTest

/**
 * Same `schedule-edge` fixture and seeding as [eu.transittrack.reports.read.AdherenceReadServiceTest]:
 * route RA has trips E1 (reaches both scheduled stops, adherenceSec 30 then -30) and E2 (reaches
 * only its first scheduled stop, adherenceSec 120). This test only exercises the GraphQL
 * controller wiring - the read-service behavior itself is covered by that test.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["transittrack.avl.enabled=true"],
)
@Import(AdherenceQueryControllerTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdherenceQueryControllerTest(
    @Autowired val graphQlTester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
    @Autowired val stopPaths: StopPathRepository,
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
            distanceAlongTripM: Double = 0.0,
        ) {
            val tripRow = trips.findByTripId(ctx.revisionId, tripId)!!
            val reportId = avlReport(vehicleId, at, tripId, routeId)
            vehicleMatches.save(
                VehicleMatch(
                    avlReportId = reportId, feedCode = feed.code, vehicleId = vehicleId, ts = at,
                    revisionId = ctx.revisionId, tripRowId = tripRow.id!!, blockPk = null,
                    tripPatternId = tripRow.tripPatternId, stopPathIndex = stopPathIndex,
                    distanceAlongTripM = distanceAlongTripM, deviationM = 0.0, scheduleAdherenceSec = adherenceSec,
                    snappedLat = 51.1, snappedLon = 17.0, heading = null, score = null,
                    createdAt = at,
                ),
            )
        }

        // E1's pattern has only 2 scheduled stops (indices 0,1), so index 1 is both E1's SECOND
        // scheduled stop and the pattern's LAST stop-path index.
        val e1Trip = trips.findByTripId(ctx.revisionId, "E1")!!
        val e1TotalLengthM =
            stopPaths.findByTripPatternOrdered(ctx.revisionId, e1Trip.tripPatternId!!).sumOf { it.lengthM }

        // E1 (route RA): reaches both scheduled stops. A VehicleMatch's reported stopPathIndex
        // means "already arrived at stop (index - 1)" (see StopPassageDerivationServiceTest's KDoc
        // for the full semantics), so arrival at scheduled stop 0 is represented by stopPathIndex 1.
        // Stop 1 is the pattern's FINAL scheduled stop, though, so it can never be represented that
        // way (SpatialMatcher.stopPathIndexAt clamps to the last stop-path index, 1, and can never
        // report 2) - instead it's a match AT index 1 whose distanceAlongTripM is at the pattern's
        // total shape length (see StopPassageDerivationService's terminal-stop special case).
        insertMatch("bus-e1", "E1", "RA", ts(8, 0), 1, 30)
        insertMatch("bus-e1", "E1", "RA", ts(8, 30), 1, -30, distanceAlongTripM = e1TotalLengthM)

        // E2 (route RA): reaches only its first scheduled stop.
        insertMatch("bus-e2", "E2", "RA", ts(9, 0), 1, 120)
    }

    @AfterAll
    fun tearDown() {
        vehicleMatches.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @Test
    fun `routeAdherence query returns stop passages`() {
        val response =
            graphQlTester
                .document(
                    """query { routeAdherence(feedCode: "g", routeId: "RA", startDate: "$startDate", endDate: "$endDate") { stopId adherenceSec } }""",
                ).execute()
        response.path("routeAdherence").entityList(Any::class.java).hasSizeGreaterThan(0)
    }

    @Test
    fun `allRoutesAdherence query returns the summary for RA`() {
        val summaries =
            graphQlTester
                .document(
                    """query { allRoutesAdherence(feedCode: "g", startDate: "$startDate", endDate: "$endDate") { routeId sampleCount meanAdherenceSec onTimePct } }""",
                ).execute()
                .path("allRoutesAdherence")
                .entityList(Map::class.java)
                .get()

        assertThat(summaries.map { it["routeId"] }).containsExactly("RA")
        val ra = summaries.single()
        assertThat(ra["sampleCount"]).isEqualTo(3)
    }

    @Test
    fun `tripAdherence query returns stop passages for the trip`() {
        val passages =
            graphQlTester
                .document(
                    """query { tripAdherence(feedCode: "g", tripId: "E1", startDate: "$startDate", endDate: "$endDate") { adherenceSec } }""",
                ).execute()
                .path("tripAdherence")
                .entityList(Map::class.java)
                .get()

        assertThat(passages.map { it["adherenceSec"] }).containsExactly(30, -30)
    }

    @Test
    fun `allTripsAdherence query returns per-trip-execution summaries`() {
        val summaries =
            graphQlTester
                .document(
                    """query { allTripsAdherence(feedCode: "g", startDate: "$startDate", endDate: "$endDate") { tripId routeId sampleCount } }""",
                ).execute()
                .path("allTripsAdherence")
                .entityList(Map::class.java)
                .get()

        assertThat(summaries.map { it["tripId"] }).containsExactly("E1", "E2")
    }

    @Test
    fun `stopAdherence query returns stop passages across routes and trips`() {
        val passages =
            graphQlTester
                .document(
                    """query { stopAdherence(feedCode: "g", stopId: "S1", startDate: "$startDate", endDate: "$endDate") { adherenceSec } }""",
                ).execute()
                .path("stopAdherence")
                .entityList(Map::class.java)
                .get()

        assertThat(passages.map { it["adherenceSec"] }.sortedBy { it as Int }).containsExactly(30, 120)
    }
}
