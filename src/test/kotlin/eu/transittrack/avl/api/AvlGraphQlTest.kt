package eu.transittrack.avl.api

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlMatchProcessor
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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
    ],
)
@Import(TestcontainersConfiguration::class, AvlGraphQlTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvlGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val factory: AvlMatchContextFactory,
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
            p: eu.transittrack.Point,
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
        insert(geom.line.pointAt(0.60 * len), ts(8, 12))

        processor.processBatch()
    }

    @AfterAll
    fun cleanup() {
        vehicleMatches.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @BeforeEach
    fun refreshVehicleReport() {
        val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-1")!!
        state.reportTs = Instant.now().minusSeconds(60)
        vehicleStates.saveAndFlush(state)
    }

    @Test
    fun `vehicles with reports older than one hour are hidden even when recently processed`() {
        val state = vehicleStates.findByFeedIdAndVehicleId(feedId, "bus-1")!!
        state.reportTs = Instant.now().minusSeconds(3601)
        state.updatedAt = Instant.now()
        vehicleStates.saveAndFlush(state)

        tester
            .document("""{ vehicles(feedCode:"a"){ vehicleId } }""")
            .execute()
            .path("vehicles")
            .entityList(Any::class.java)
            .hasSize(0)
        tester
            .document("""{ vehicles(feedCode:"a", matchedOnly:true){ vehicleId } }""")
            .execute()
            .path("vehicles")
            .entityList(Any::class.java)
            .hasSize(0)
        tester
            .document("""{ vehicle(feedCode:"a", vehicleId:"bus-1"){ vehicleId } }""")
            .execute()
            .path("vehicle")
            .valueIsNull()
    }

    @Test
    fun `avlFeeds lists the registered feed`() {
        val codes =
            tester
                .document("{ avlFeeds { code assignmentMode gtfsFeedCode } }")
                .execute()
                .path("avlFeeds")
                .entityList(Map::class.java)
                .get()
        val feed = codes.first { it["code"] == "a" }
        assertThat(feed["assignmentMode"]).isEqualTo("FULL_INFERENCE")
        assertThat(feed["gtfsFeedCode"]).isEqualTo("g")
    }

    @Test
    fun `vehicle resolves matched state with nested trip and pattern`() {
        val v =
            tester
                .document(
                    """{ vehicle(feedCode:"a", vehicleId:"bus-1"){
                         matched scheduleAdherenceSec position { lat lon }
                         trip { tripId } pattern { patternKey }
                       } }""",
                ).execute()
                .path("vehicle")
                .entity(Map::class.java)
                .get()
        assertThat(v["matched"]).isEqualTo(true)
        @Suppress("UNCHECKED_CAST")
        assertThat((v["trip"] as Map<String, Any?>)["tripId"]).isEqualTo("T1")
        @Suppress("UNCHECKED_CAST")
        assertThat((v["pattern"] as Map<String, Any?>)["patternKey"]).isNotNull()
    }

    @Test
    fun `vehicles matchedOnly contains bus-1`() {
        val ids =
            tester
                .document("""{ vehicles(feedCode:"a", matchedOnly:true){ vehicleId } }""")
                .execute()
                .path("vehicles")
                .entityList(Map::class.java)
                .get()
                .map { it["vehicleId"] }
        assertThat(ids).isNotEmpty()
        assertThat(ids).contains("bus-1")
    }

    @Test
    fun `avlReports returns the stored reports`() {
        tester
            .document("""{ avlReports(feedCode:"a", vehicleId:"bus-1"){ ts position { lat } } }""")
            .execute()
            .path("avlReports")
            .entityList(Any::class.java)
            .hasSize(2)
    }

    @Test
    fun `avlReports carries matchStatus and nested match info per report`() {
        val results =
            tester
                .document(
                    """{ avlReports(feedCode:"a", vehicleId:"bus-1"){
                         matchStatus
                         match { stopPathIndex distanceAlongTripM deviationM trip { tripId } }
                       } }""",
                ).execute()
                .path("avlReports")
                .entityList(Map::class.java)
                .get()

        assertThat(results).isNotEmpty()
        for (r in results) {
            assertThat(r["matchStatus"]).isEqualTo("MATCHED")
            @Suppress("UNCHECKED_CAST")
            val match = r["match"] as Map<String, Any?>
            assertThat((match["trip"] as Map<*, *>)["tripId"]).isEqualTo("T1")
        }
    }
}
