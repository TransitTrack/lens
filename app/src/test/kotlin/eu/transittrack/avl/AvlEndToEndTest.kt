package eu.transittrack.avl

import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
import eu.transittrack.support.PostgresPerClassTest
import eu.transittrack.support.RestGet
import eu.transittrack.util.paging.PageRequest

/**
 * Full AVL pipeline through a real HTTP endpoint: a GTFS-RT `VehiclePosition` feed served by
 * [MockWebServer] is polled by [AvlIngestService], the resulting `avl_report` rows are matched by
 * [AvlMatchProcessor] against the derived schedule model, and the `vehicle_state` outcome is read
 * back over REST.
 */
@Disabled
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.feed.feeds=",
    ],
)
@Import(AvlEndToEndTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvlEndToEndTest(
    @LocalServerPort val port: Int,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val ingestService: AvlIngestService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val contextFactory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
) : PostgresPerClassTest() {
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

        vehicleMatches.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()

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
    fun `poll, match, advance, then stale on a far report - all visible over REST`() {
        val feedRow = feeds.findByCode("mbta-vp")!!
        val ctx = contextFactory.open(feedRow)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val len = geom.line.lengthM
        val p25 = geom.line.pointAt(len * 0.25)
        val p60 = geom.line.pointAt(len * 0.60)
        val far = Point(p25.lat + 0.05, p25.lon + 0.05)

        val svcTs = { minute: Int ->
            LocalDateTime
                .now()
                .plusMinutes(minute.toLong())
                .atZone(ctx.zone)
                .toInstant()
        }

        // --- first poll: on-shape at 25% ---
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p25, svcTs(1).epochSecond))))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        assertThat(
            reports.findByVehicle(feedRow.code, "bus-1", PageRequest(0, 2)).size,
        ).isEqualTo(1)

        assertThat(processor.processBatch()).isEqualTo(1)
        val s1 = vehicleStates.findByFeedCodeAndVehicleId(feedRow.code, "bus-1")!!
        assertThat(s1.matched).isTrue()
        assertThat(s1.tripRowId).isEqualTo(trip.id)
        assertThat(s1.distanceAlongTripM!!).isCloseTo(0.25 * len, 0.1 * len)

        // --- second poll: advanced to 60% ---
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(p60, svcTs(9).epochSecond))))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        processor.processBatch()
        val s2 = vehicleStates.findByFeedCodeAndVehicleId(feedRow.code, "bus-1")!!
        assertThat(s2.matched).isTrue()
        assertThat(s2.distanceAlongTripM!!).isGreaterThan(s1.distanceAlongTripM!!)
        assertThat(s2.consecutiveFailures).isEqualTo(0)

        // --- third poll: far off-shape -> stale, keeps last assignment ---
        server.enqueue(MockResponse().setBody(Buffer().write(feedMessageBytes(far, svcTs(15).epochSecond))))
        assertThat(ingestService.pollOnce(feedRow)).isEqualTo(1)
        processor.processBatch()
        val s3 = vehicleStates.findByFeedCodeAndVehicleId(feedRow.code, "bus-1")!!
        assertThat(s3.matched).isFalse()
        assertThat(s3.stale).isTrue()
        assertThat(s3.consecutiveFailures).isEqualTo(1)
        assertThat(s3.tripRowId).isEqualTo(trip.id)

        // --- REST surface ---
        // The schedule fixture uses a fixed date; expose a current vehicle state for this read.
        s3.reportTs = Instant.now()
        vehicleStates.save(s3)
        val v = RestGet(port)("/api/v1/avl-feeds/mbta-vp/vehicles/bus-1").jsonObject
        assertThat(v.getValue("matched").jsonPrimitive.boolean).isEqualTo(false)
        assertThat(v.getValue("stale").jsonPrimitive.boolean).isEqualTo(true)
        assertThat(
            v
                .getValue("trip")
                .jsonObject
                .getValue("tripId")
                .jsonPrimitive.content,
        ).isEqualTo("T1")
    }
}
