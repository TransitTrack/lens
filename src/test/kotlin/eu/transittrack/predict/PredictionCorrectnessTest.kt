package eu.transittrack.predict

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.match.cache.CachedLearningKeyReader
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.generate.serviceSecToInstant
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.KalmanTravelTimeState
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservation
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
        "transittrack.feed.feeds=",
    ],
)
@Import(PredictionCorrectnessTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PredictionCorrectnessTest(
    @Autowired val service: PredictionService,
    @Autowired val writer: PredictionWriter,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val keys: CachedLearningKeyReader,
    @Autowired val predictions: VehiclePredictionRepository,
    @Autowired val crossings: AvlStopCrossingRepository,
    @Autowired val observations: TravelTimeObservationRepository,
    @Autowired val states: KalmanTravelTimeStateRepository,
    @Autowired val accuracy: PredictionAccuracyRepository,
    @Autowired val jdbc: JdbcTemplate,
) : PostgresPerClassTest() {
    @MockitoBean
    lateinit var clock: Clock

    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private val date = LocalDate.of(2026, 9, 7)
    private lateinit var feed: AvlFeed
    private lateinit var ctx: AvlMatchContext
    private var previous: PrevVehicleMatch? = null

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    @BeforeEach
    fun seed() {
        feed = feeds.save(
            AvlFeed(
                code = "correctness", name = "Correctness", gtfsFeedCode = "g", url = "x",
                format = AvlFormat.GTFS_RT, pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR,
                enabled = true, headers = null, source = AvlFeedSourceKind.CONFIG,
                predictionAlgorithms = PredictionAlgorithm.entries, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
        ctx = factory.open(feed)!!
        previous = null
    }

    @AfterEach
    fun cleanup() {
        jdbc.update("delete from prediction_checkpoint")
        accuracy.deleteAll()
        predictions.deleteAll()
        crossings.deleteAll()
        observations.deleteAll()
        states.deleteAll()
        feeds.deleteById(feed.id!!)
    }

    private fun start(): Instant = serviceSecToInstant(date, 8 * 3600 + 5 * 60, ctx.zone)

    private fun advance(
        tripId: String,
        alongM: Double,
        at: Instant,
        receivedAt: Instant = at,
        issuedAt: Instant = receivedAt,
        startTimeSec: Int? = null,
    ) {
        val trip = ctx.tripByGtfsId(tripId)!!
        val patternId = trip.tripPatternId!!
        val geom = ctx.patternGeometry(patternId)!!
        val point = geom.line.pointAt(alongM)
        val index = geom.stopPathCumM.indexOfLast { it <= alongM }.coerceAtLeast(0)
        val report = AvlReportRow(
            feedCode = feed.code, vehicleId = "bus", vehicleLabel = null, ts = at, lat = point.lat, lon = point.lon,
            bearing = null, speedMps = null, odometerM = null, descTripId = tripId, descRouteId = trip.routeId,
            descDirectionId = trip.directionId, descStartDate = date, descStartTimeSec = startTimeSec,
            descScheduleRelationship = null, currentStopSequence = null, currentStopId = null, currentStatus = null,
            occupancyStatus = null, congestionLevel = null, matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
        ).apply { id = 1L }
        val outcome = MatchOutcome.Matched(
            tripRowId = trip.id, blockPk = ctx.blockTripOf(trip.id)?.blockId, tripPatternId = patternId,
            stopPathIndex = index, distanceAlongTripM = alongM, deviationM = 0.0, scheduleAdherenceSec = 0,
            snapped = point, heading = null, score = null, revisionId = ctx.revisionId,
        )
        whenever(clock.instant()).thenReturn(receivedAt, receivedAt, issuedAt)
        service.onMatched(feed, report, previous, outcome, ctx)
        previous = PrevVehicleMatch(trip.id, index, alongM, at)
    }

    @Test
    fun `algorithms with different ETAs are scored at the same actual issued-time horizon`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val patternId = trip.tripPatternId!!
        val geom = ctx.patternGeometry(patternId)!!
        val key = keys.keyOf(ctx.revisionId, patternId)!!
        val at = start()
        observations.save(
            TravelTimeObservation(
                gtfsFeedCode = key.gtfsFeedCode, patternKey = key.patternKey, stopPathIndex = 1,
                dayType = DayType.ALL, timeBand = TimeBand.ALL, sampleCount = 100, meanSec = 120.0, updatedAt = at,
            ),
        )
        states.save(
            KalmanTravelTimeState(
                gtfsFeedCode = key.gtfsFeedCode, patternKey = key.patternKey, stopPathIndex = 1,
                dayType = DayType.ALL, timeBand = TimeBand.ALL, sampleCount = 100, estimateSec = 120.0,
                errorVariance = 64.0, updatedAt = at,
            ),
        )
        advance("T1", (geom.stopPathCumM[1] + geom.stopPathCumM[2]) / 2, at, at.plusSeconds(30))
        advance("T1", geom.stopPathCumM[2] + 10, at.plusSeconds(120))
        val scored = accuracy.findAll().filter { it.stopPathIndex == 1 }
        assertThat(scored.size).isEqualTo(3)
        assertThat(scored.map { it.predictedTs }.toSet().size).isEqualTo(2)
        assertThat(scored.map { it.horizonSec }.toSet()).isEqualTo(setOf(180))
        assertThat(scored.all { it.predictedAt == at && it.producedAt == at.plusSeconds(30) }).isEqualTo(true)
        scored.forEach {
            assertThat(it.actualLeadSec?.toLong()).isEqualTo(Duration.between(at.plusSeconds(30), it.actualTs).seconds)
        }
    }

    @Test
    fun `predictions produced after an interpolated actual are not backtested as real forecasts`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val boundary = geom.stopPathCumM[2]
        val at = start()
        advance("T1", boundary - 10, at, issuedAt = at.plusSeconds(90))
        assertThat(predictions.findAll().filter { it.stopPathIndex == 1 }).isNotEmpty()
        advance("T1", boundary + 10, at.plusSeconds(60), at.plusSeconds(120))
        assertThat(accuracy.findAll().filter { it.stopPathIndex == 1 }).isEmpty()
    }

    @Test
    fun `a source that expires during processing cannot publish a fresh-looking prediction`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val at = start()
        advance("T1", geom.stopPathCumM[2] - 10, at, issuedAt = at.plusSeconds(601))
        assertThat(predictions.findAll()).isEmpty()
        assertThat(jdbc.queryForObject("select count(*) from prediction_checkpoint", Int::class.java)).isEqualTo(0)
    }

    @Test
    fun `stale reports still teach reliable crossings but never create live predictions`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val at = start()
        for ((along, elapsed) in listOf(
            geom.stopPathCumM[2] - 10 to 0L,
            geom.stopPathCumM[2] + 10 to 60L,
            geom.stopPathCumM[3] - 10 to 300L,
            geom.stopPathCumM[3] + 10 to 360L,
        )) {
            advance("T1", along, at.plusSeconds(elapsed), at.plusSeconds(elapsed + 3600))
        }
        assertThat(crossings.findAll().size).isEqualTo(2)
        assertThat(observations.findAll()).isNotEmpty()
        assertThat(states.findAll()).isNotEmpty()
        assertThat(predictions.findAll()).isEmpty()
        assertThat(jdbc.queryForObject("select count(*) from prediction_checkpoint", Int::class.java)).isEqualTo(0)
    }

    @Test
    fun `a sparse endpoint disqualifies learning and its own accuracy but preserves actuals`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val at = start()
        advance("T1", geom.stopPathCumM[2] - 10, at)
        advance("T1", geom.stopPathCumM[2] + 10, at.plusSeconds(120).plusNanos(1000))
        advance("T1", geom.stopPathCumM[3] - 10, at.plusSeconds(180))
        advance("T1", geom.stopPathCumM[3] + 10, at.plusSeconds(240))
        val passes = crossings.findAll().sortedBy { it.stopPathIndex }
        assertThat(passes.map { it.reportGapSec }).isEqualTo(listOf(121, 60))
        assertThat(passes.all { it.observedTravelTimeSec == null }).isEqualTo(true)
        assertThat(observations.findAll()).isEmpty()
        assertThat(states.findAll()).isEmpty()
        assertThat(accuracy.findAll().filter { it.stopPathIndex == 1 }).isEmpty()
        val actuals = predictions.findAll().filter { it.tripRowId == trip.id && it.stopPathIndex == 1 }
        assertThat(actuals).isNotEmpty()
        assertThat(actuals.all { it.actualArrivalTs != null && it.actualReportGapSec == 121 }).isEqualTo(true)
    }

    @Test
    fun `the report-gap quality limit includes exactly 120 seconds`() {
        val trip = ctx.tripByGtfsId("T1")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val at = start()
        advance("T1", geom.stopPathCumM[2] - 10, at)
        advance("T1", geom.stopPathCumM[2] + 10, at.plusSeconds(120))
        assertThat(crossings.findAll().single().reportGapSec).isEqualTo(120)
        assertThat(accuracy.findAll().filter { it.stopPathIndex == 1 }).isNotEmpty()
    }

    @Test
    fun `frequency run persists before its first crossing and does not reload the previous lap`() {
        val trip = ctx.tripByGtfsId("T5")!!
        val patternId = trip.tripPatternId!!
        val geom = ctx.patternGeometry(patternId)!!
        val at = start()
        val oldRun = at.minusSeconds(3600)
        crossings.saveAll(
            listOf(0 to oldRun, 1 to at.minusSeconds(2700)).map { (index, time) ->
                AvlStopCrossing(
                    feedCode = feed.code, vehicleId = "bus", tripRowId = trip.id, tripPatternId = patternId,
                    stopPathIndex = index, observedAt = time, observedTravelTimeSec = null, serviceId = trip.serviceId,
                    tripStartSec = trip.startTimeSec, routeId = trip.routeId, directionId = trip.directionId,
                    revisionId = ctx.revisionId, runStartedAt = oldRun, reportGapSec = 30,
                )
            },
        )
        predictions.save(
            VehiclePrediction(
                feedCode = feed.code, vehicleId = "bus", tripRowId = trip.id, blockPk = null, tripPatternId = patternId,
                stopPathIndex = 0, algorithm = PredictionAlgorithm.HISTORICAL_AVERAGE,
                predictedArrivalTs = oldRun, predictedDepartureTs = oldRun, actualArrivalTs = null,
                actualDepartureTs = oldRun, confidenceSec = null, computedAt = at.minusSeconds(2700),
                sourceReportTs = at.minusSeconds(2700), serviceDate = date,
            ),
        )
        advance("T5", 0.0, at)
        val firstStop = predictions.findAll().single { it.stopPathIndex == 0 && it.algorithm == PredictionAlgorithm.HISTORICAL_AVERAGE }
        assertThat(firstStop.actualDepartureTs).isNull()
        assertThat(firstStop.sourceReportTs).isEqualTo(at)
        advance("T5", 15.0, at.plusSeconds(30))
        advance("T5", 60.0, at.plusSeconds(60))
        advance("T5", geom.line.lengthM - 10, at.plusSeconds(120))
        val current = crossings.findAll().filter { it.runStartedAt == at }.sortedBy { it.stopPathIndex }
        assertThat(current.map { it.stopPathIndex }).isEqualTo(listOf(0, 1))
        assertThat(crossings.count()).isEqualTo(4L)
        assertThat(current.last().observedTravelTimeSec != null).isEqualTo(true)
        assertThat(accuracy.findAll().filter { it.stopPathIndex == 0 && it.predictedTs != null }).isNotEmpty()
        writer.inTransaction { session ->
            assertThat(writer.frequencyRun(session, feed.code, "bus", trip.id)?.startedAt).isEqualTo(at)
        }
    }

    @Test
    fun `returning to a frequency trip after another assignment starts a new run without inheriting its descriptor`() {
        val trip = ctx.tripByGtfsId("T5")!!
        val geom = ctx.patternGeometry(trip.tripPatternId!!)!!
        val at = start()
        advance("T5", 0.0, at, startTimeSec = 8 * 3600)
        advance("T5", 60.0, at.plusSeconds(30))
        advance("T5", geom.line.lengthM - 10, at.plusSeconds(90))
        advance("T1", 300.0, at.plusSeconds(120))
        val nextRun = at.plusSeconds(150)
        advance("T5", 0.0, nextRun)
        val firstStop = predictions.findAll().single {
            it.tripRowId == trip.id && it.stopPathIndex == 0 && it.algorithm == PredictionAlgorithm.HISTORICAL_AVERAGE
        }
        assertThat(firstStop.actualDepartureTs).isNull()
        assertThat(firstStop.sourceReportTs).isEqualTo(nextRun)
        writer.inTransaction { session ->
            val run = writer.frequencyRun(session, feed.code, "bus", trip.id)!!
            assertThat(run.startedAt).isEqualTo(nextRun)
            assertThat(run.startTimeSec).isNull()
            assertThat(run.lastDistanceAlongTripM).isEqualTo(0.0)
        }
        advance("T5", 60.0, at.plusSeconds(180), startTimeSec = 8 * 3600 + 5 * 60)
        val departed = crossings.findAll().single { it.runStartedAt == nextRun }
        assertThat(departed.stopPathIndex).isEqualTo(0)
        assertThat(crossings.count()).isEqualTo(3L)
        writer.inTransaction { session ->
            assertThat(writer.frequencyRun(session, feed.code, "bus", trip.id)?.startedAt).isEqualTo(nextRun)
        }
    }
}
