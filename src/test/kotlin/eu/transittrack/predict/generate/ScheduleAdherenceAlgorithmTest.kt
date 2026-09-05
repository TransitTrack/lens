package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
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
import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, ScheduleAdherenceAlgorithmTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleAdherenceAlgorithmTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
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

    @Test
    fun `serviceSecToInstant round-trips`() {
        val zone = ZoneId.systemDefault()
        val serviceDate = LocalDate.of(2026, 9, 7)
        val instant = serviceSecToInstant(serviceDate, 28800, zone)

        val roundTripSec = Duration.between(serviceDate.atStartOfDay(zone).toInstant(), instant).seconds

        assertThat(roundTripSec).isEqualTo(28800L)
    }

    @Test
    fun `buildHorizon walks remaining stops of T1 and into its block`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!

        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx)

        assertThat(horizon.isEmpty()).isFalse()
        assertThat(horizon.first().stopPathIndex).isEqualTo(1)
        assertThat(horizon.first().tripRowId).isEqualTo(trip.id)
        assertThat(horizon.none { it.stopPathIndex < 0 }).isTrue()
    }

    @Test
    fun `predict projects schedule time forward by adherence`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val serviceDate = LocalDate.of(2026, 9, 7)

        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, ctx)
        assertThat(horizon.size).isGreaterThan(0)

        val targetIndex = horizon.first().stopPathIndex
        val schedulePoint = ctx.scheduleOf(trip.id!!).first { it.stopPathIndex == targetIndex }
        val rawArrivalSec = schedulePoint.arrivalSec
        assertThat(rawArrivalSec).isNotNull()

        val rawInstant = serviceSecToInstant(serviceDate, rawArrivalSec!!, ctx.zone)

        val predictions = ScheduleAdherenceAlgorithm().predict(horizon, serviceDate, adherenceSec = 30, startTs = Instant.EPOCH, ctx)
        val prediction = predictions.first { it.tripRowId == trip.id && it.stopPathIndex == targetIndex }

        assertThat(prediction.predictedArrivalTs).isEqualTo(rawInstant.plusSeconds(30))
    }
}
