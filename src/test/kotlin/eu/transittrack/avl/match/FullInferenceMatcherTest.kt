package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleStateRow
import eu.transittrack.feed.AvlAssignmentMode
import eu.transittrack.feed.AvlFormat
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, FullInferenceMatcherTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullInferenceMatcherTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val matcher: FullInferenceMatcher,
) {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    private val avlFeed =
        AvlFeed(
            code = "a",
            name = "A",
            gtfsFeedCode = "g",
            url = "x",
            format = AvlFormat.GTFS_RT,
            pollIntervalSec = 15,
            assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
            enabled = true,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun report(
        lat: Double,
        lon: Double,
        ts: Instant,
    ): AvlReportRow =
        AvlReportRow(
            feedId = 1L,
            vehicleId = "v",
            vehicleLabel = null,
            ts = ts,
            lat = lat,
            lon = lon,
            bearing = null,
            speedMps = null,
            odometerM = null,
            descTripId = null,
            descRouteId = null,
            descDirectionId = null,
            descStartDate = LocalDate.of(2026, 9, 7),
            descStartTimeSec = null,
            descScheduleRelationship = null,
            currentStopSequence = null,
            currentStopId = null,
            currentStatus = null,
            occupancyStatus = null,
            congestionLevel = null,
            matchStatus = MatchStatus.PENDING,
            matchedAt = null,
            createdAt = ts,
        )

    private fun prevOnTrip(
        tripRowId: Long,
        alongM: Double,
    ): VehicleStateRow =
        VehicleStateRow(
            feedId = 1L,
            vehicleId = "v",
            vehicleLabel = null,
            reportTs = Instant.EPOCH,
            lat = 0.0,
            lon = 0.0,
            bearing = null,
            speedMps = null,
            occupancyStatus = null,
            matched = true,
            stale = false,
            consecutiveFailures = 0,
            revisionId = null,
            tripRowId = tripRowId,
            blockPk = null,
            tripPatternId = null,
            stopPathIndex = null,
            distanceAlongTripM = alongM,
            scheduleAdherenceSec = null,
            snappedLat = null,
            snappedLon = null,
            updatedAt = Instant.EPOCH,
        )

    private fun ts0805() =
        LocalDate
            .of(2026, 9, 7)
            .atTime(8, 5)
            .atZone(factory.open(avlFeed)!!.zone)
            .toInstant()

    @Test
    fun `on-shape T1 midpoint with no prev matches T1`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)

        val outcome = matcher.match(report(mid.lat, mid.lon, ts0805()), prev = null, ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        assertThat((outcome as MatchOutcome.Matched).tripRowId).isEqualTo(trip.id)
    }

    @Test
    fun `advancing along T1 stays matched with larger distance`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val len = geom.line.lengthM
        val p = geom.line.pointAt(0.6 * len)

        val outcome = matcher.match(report(p.lat, p.lon, ts0805()), prevOnTrip(trip.id!!, 0.4 * len), ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        val m = outcome as MatchOutcome.Matched
        assertThat(m.tripRowId).isEqualTo(trip.id)
        assertThat(m.distanceAlongTripM > 0.4 * len).isTrue()
    }

    @Test
    fun `garbage position with no prev fails`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)

        assertThat(matcher.match(report(mid.lat + 0.05, mid.lon + 0.05, ts0805()), prev = null, ctx))
            .isEqualTo(MatchOutcome.Failed)
    }
}
