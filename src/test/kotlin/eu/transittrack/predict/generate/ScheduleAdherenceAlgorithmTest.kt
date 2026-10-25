package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
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

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(ScheduleAdherenceAlgorithmTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleAdherenceAlgorithmTest(
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val trips: TripRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
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
    fun `serviceSecToInstant keeps wall-clock times on the day DST ends`() {
        val zone = ZoneId.of("Europe/Bucharest")
        val dstEnd = LocalDate.of(2026, 10, 25)

        val instant = serviceSecToInstant(dstEnd, 6 * 3600 + 11 * 60, zone)

        assertThat(instant).isEqualTo(dstEnd.atTime(6, 11).atZone(zone).toInstant())
    }

    /** Halfway along T1's stop path 1: the vehicle has left the first stop. */
    private fun midPath1(
        ctx: eu.transittrack.avl.match.AvlMatchContext,
        tripPatternId: Long,
    ): Double {
        val cum = ctx.patternGeometry(tripPatternId)!!.stopPathCumM
        return (cum[1] + cum[2]) / 2.0
    }

    private val serviceDate = LocalDate.of(2026, 9, 7)

    @Test
    fun `buildHorizon walks remaining stops of T1 and into its block`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!

        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, midPath1(ctx, trip.tripPatternId!!), ctx)

        assertThat(horizon.isEmpty()).isFalse()
        assertThat(horizon.first().stopPathIndex).isEqualTo(1)
        assertThat(horizon.first().tripRowId).isEqualTo(trip.id)
        assertThat(horizon.first().remainingFraction).isEqualTo(0.5)
        assertThat(horizon.drop(1).all { it.remainingFraction == 1.0 }).isTrue()
        assertThat(horizon.none { it.stopPathIndex < 0 }).isTrue()
    }

    @Test
    fun `buildHorizon keeps the first stop for a vehicle still standing at it`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!

        // GPS noise projects a waiting vehicle a few metres onto stop path 1.
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, distanceAlongTripM = 5.0, ctx)

        assertThat(horizon.first().stopPathIndex).isEqualTo(0)
        assertThat(horizon[1].stopPathIndex).isEqualTo(1)
        assertThat(horizon[1].remainingFraction).isEqualTo(1.0)
    }

    @Test
    fun `predict projects schedule time forward by adherence`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!

        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, midPath1(ctx, trip.tripPatternId!!), ctx)
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

    @Test
    fun `being early at the first stop is absorbed by its scheduled departure`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val schedule = ctx.scheduleOf(trip.id!!)
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, distanceAlongTripM = 5.0, ctx)

        // Standing at the terminus five minutes before departure reads as -300s adherence.
        val predictions = ScheduleAdherenceAlgorithm().predict(horizon, serviceDate, adherenceSec = -300, startTs = Instant.EPOCH, ctx)

        val stop0 = predictions.first { it.tripRowId == trip.id && it.stopPathIndex == 0 }
        val stop1 = predictions.first { it.tripRowId == trip.id && it.stopPathIndex == 1 }
        val departure0 = schedule.first { it.stopPathIndex == 0 }.departureSec!!
        val arrival1 = schedule.first { it.stopPathIndex == 1 }.arrivalSec!!
        assertThat(stop0.predictedDepartureTs).isEqualTo(serviceSecToInstant(serviceDate, departure0, ctx.zone))
        assertThat(stop1.predictedArrivalTs).isEqualTo(serviceSecToInstant(serviceDate, arrival1, ctx.zone))
    }

    @Test
    fun `lateness carries into the next block trip only beyond its layover`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, midPath1(ctx, trip.tripPatternId!!), ctx)
        val nextTripRowId = horizon.first { it.tripRowId != trip.id }.tripRowId
        val lastArrivalSec = ctx.scheduleOf(trip.id!!).last().arrivalSec!!
        val nextDepartureSec = ctx.scheduleOf(nextTripRowId).first { it.stopPathIndex == 0 }.departureSec!!
        val layoverSec = nextDepartureSec - lastArrivalSec
        assertThat(layoverSec).isGreaterThan(0)
        val scheduledNextDeparture = serviceSecToInstant(serviceDate, nextDepartureSec, ctx.zone)

        fun nextTripDeparture(adherenceSec: Int) =
            ScheduleAdherenceAlgorithm()
                .predict(horizon, serviceDate, adherenceSec, Instant.EPOCH, ctx)
                .first { it.tripRowId == nextTripRowId && it.stopPathIndex == 0 }
                .predictedDepartureTs

        assertThat(nextTripDeparture(layoverSec / 2)).isEqualTo(scheduledNextDeparture)
        assertThat(nextTripDeparture(layoverSec + 120)).isEqualTo(scheduledNextDeparture.plusSeconds(120))
    }

    @Test
    fun `no usable adherence predicts nothing rather than assuming on time`() {
        val ctx = factory.open(avlFeed)!!
        val trip = trips.findByTripId(ctx.revisionId, "T1")!!
        val horizon = buildHorizon(trip.id!!, trip.tripPatternId!!, fromStopPathIndex = 1, midPath1(ctx, trip.tripPatternId!!), ctx)

        assertThat(ScheduleAdherenceAlgorithm().predict(horizon, serviceDate, adherenceSec = null, Instant.EPOCH, ctx)).isEmpty()
    }
}
