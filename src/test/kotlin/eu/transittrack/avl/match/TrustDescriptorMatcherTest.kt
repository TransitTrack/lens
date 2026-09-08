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

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, TrustDescriptorMatcherTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustDescriptorMatcherTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val matcher: TrustDescriptorMatcher,
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
            assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
            enabled = true,
            headers = null,
            source = AvlFeedSourceKind.CONFIG,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun report(
        lat: Double,
        lon: Double,
        descTripId: String?,
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
            descTripId = descTripId,
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

    @Test
    fun `on-shape point on T1 matches T1`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)
        val ts = LocalDate
            .of(2026, 9, 7)
            .atTime(8, 5)
            .atZone(ctx.zone)
            .toInstant()

        val outcome = matcher.match(report(mid.lat, mid.lon, "T1", ts), prev = null, ctx)

        assertThat(outcome).isInstanceOf(MatchOutcome.Matched::class)
        val m = outcome as MatchOutcome.Matched
        assertThat(m.tripRowId).isEqualTo(trip.id)
        val len = geom.line.lengthM
        assertThat(m.distanceAlongTripM in (0.3 * len)..(0.7 * len)).isTrue()
    }

    @Test
    fun `unknown descTripId fails`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)
        val ts = LocalDate
            .of(2026, 9, 7)
            .atTime(8, 5)
            .atZone(ctx.zone)
            .toInstant()

        assertThat(matcher.match(report(mid.lat, mid.lon, "NOPE", ts), null, ctx))
            .isEqualTo(MatchOutcome.Failed)
    }

    @Test
    fun `point far off the shape fails`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val mid = geom.line.pointAt(geom.line.lengthM / 2.0)
        val ts = LocalDate
            .of(2026, 9, 7)
            .atTime(8, 5)
            .atZone(ctx.zone)
            .toInstant()

        assertThat(matcher.match(report(mid.lat + 0.02, mid.lon + 0.02, "T1", ts), null, ctx))
            .isEqualTo(MatchOutcome.Failed)
    }
}
