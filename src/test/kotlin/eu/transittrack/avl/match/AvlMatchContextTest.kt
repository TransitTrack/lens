package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.TripPatternRepository

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, AvlMatchContextTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvlMatchContextTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val feeds: GtfsFeedRepository,
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

    @Test
    fun `open binds to the active revision`() {
        val ctx = factory.open(avlFeed)
        assertThat(ctx).isNotNull()
        val expected = revisionService.activeRevisionId(feeds.findByCode("g")!!.id!!)
        assertThat(ctx!!.revisionId).isEqualTo(expected)
    }

    @Test
    fun `pattern geometry is built and cumulative distances match stop count`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        assertThat(trip.tripPatternId).isNotNull()
        val pattern = patterns.findById(trip.tripPatternId!!).orElseThrow()
        val geom = ctx.patternGeometry(trip.tripPatternId!!)
        assertThat(geom).isNotNull()
        assertThat(geom!!.line.lengthM).isGreaterThan(0.0)
        assertThat(geom.stopPathCumM.size).isEqualTo(pattern.stopCount)
    }

    @Test
    fun `schedule of a trip is non-empty and ordered by stop path index`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val schedule = ctx.scheduleOf(trip.id!!)
        assertThat(schedule).isNotEmpty()
        val indices = schedule.map { it.stopPathIndex }
        assertThat(indices).isEqualTo(indices.sorted())
    }

    @Test
    fun `active service ids include WK on a weekday`() {
        val ctx = factory.open(avlFeed)!!
        assertThat(ctx.activeServiceIds(LocalDate.of(2026, 9, 7))).contains("WK")
    }
}
