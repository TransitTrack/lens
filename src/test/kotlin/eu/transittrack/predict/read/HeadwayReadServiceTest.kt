package eu.transittrack.predict.read

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
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
import eu.transittrack.Point
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlMatchProcessor
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.support.PostgresPerClassTest

/**
 * `schedule-sample` facts used here: trips T1 and T2 are both route RA / direction 0 / service WK,
 * and share the same shape (SHP_OUT) with the identical stop sequence S1,S2,S3,S4 - so they derive
 * to the same TripPattern and both serve stop S3 as stopPathIndex 2. Stop S1 (index 0, the very
 * first stop of that pattern) is deliberately NOT used here: only a vehicle still standing at the
 * terminus (within a few metres of it) has its first stop in the horizon; anywhere further along
 * the shape it's matched to stopPathIndex 1 already and the horizon starts at the next stop.
 * Route RA also has T3 serving S3 in direction 0 (on its own, shorter pattern S1,S3,S4), which is
 * what lets the scheduled-headway average be computed from >= 2 trips.
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(HeadwayReadServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HeadwayReadServiceTest(
    @Autowired val headwayReadService: HeadwayReadService,
    @Autowired val read: PredictionReadService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val processor: AvlMatchProcessor,
    @Autowired val predictionProcessor: eu.transittrack.predict.PredictionProcessor,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleStates: VehicleStateRepository,
    @Autowired val vehiclePredictions: VehiclePredictionRepository,
    @Autowired val travelTimeObservations: TravelTimeObservationRepository,
    @Autowired val kalmanStates: KalmanTravelTimeStateRepository,
    @Autowired val predictionAccuracies: PredictionAccuracyRepository,
    @Autowired val clock: Clock,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")

        @Bean
        @Primary
        fun testClock(): Clock = Clock.fixed(Instant.parse("2026-09-07T06:57:00Z"), ZoneOffset.UTC)
    }

    private var feedId = 0L

    @BeforeAll
    fun setup() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "a", name = "A", gtfsFeedCode = "g", url = "x",
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
        ): Instant =
            LocalDate
                .of(2026, 9, 7)
                .atTime(hour, minute)
                .atZone(ctx.zone)
                .toInstant()

        fun insert(
            vehicleId: String,
            tripId: String,
            p: Point,
            at: Instant,
        ) {
            reports.save(
                AvlReportRow(
                    feedCode = feed.code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                    lat = p.lat, lon = p.lon, bearing = null, speedMps = null, odometerM = null,
                    descTripId = tripId, descRouteId = "RA", descDirectionId = 0,
                    descStartDate = LocalDate.of(2026, 9, 7), descStartTimeSec = null,
                    descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                    currentStatus = null, occupancyStatus = null, congestionLevel = null,
                    matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                ),
            )
        }

        val t1 = trips.findByTripId(ctx.revisionId, "T1")!!
        val t2 = trips.findByTripId(ctx.revisionId, "T2")!!
        val t1Geom = ctx.patternGeometry(t1.tripPatternId!!)!!
        val t2Geom = ctx.patternGeometry(t2.tripPatternId!!)!!

        insert("bus-1", "T1", t1Geom.line.pointAt(0.02 * t1Geom.line.lengthM), ts(8, 55))
        insert("bus-2", "T2", t2Geom.line.pointAt(0.02 * t2Geom.line.lengthM), ts(8, 57))

        processor.processBatch()
        predictionProcessor.processBatch()
    }

    @AfterAll
    fun tearDown() {
        predictionAccuracies.deleteAll()
        vehiclePredictions.deleteAll()
        travelTimeObservations.deleteAll()
        kalmanStates.deleteAll()
        vehicleStates.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @Test
    fun `headway reports one gap between two matched vehicles and a scheduled baseline`() {
        val headway = headwayReadService.headway(feedCode = "a", stopId = "S3", routeId = "RA", directionId = 0)

        assertThat(headway.gapsSec).hasSize(1)
        assertThat(headway.gapsSec.first()).isGreaterThanOrEqualTo(0)
        assertThat(headway.waitSec).isNotNull()
        assertThat(headway.waitSec!!).isGreaterThanOrEqualTo(0)
        assertThat(headway.scheduledHeadwaySec).isNotNull()
    }

    @Test
    fun `a vehicle whose predictions stopped being refreshed drops off the stop board`() {
        val bus2 = vehiclePredictions.findAll().filter { it.vehicleId == "bus-2" }
        val sourceAt = bus2.associate { it.id to it.sourceReportTs }
        try {
            // bus-2 went silent an hour ago: its last predictions are no longer live arrivals.
            bus2.forEach { it.sourceReportTs = clock.instant().minus(Duration.ofHours(1)) }
            vehiclePredictions.saveAll(bus2)

            val headway = headwayReadService.headway(feedCode = "a", stopId = "S3", routeId = "RA", directionId = 0)

            assertThat(headway.gapsSec).isEmpty()
        } finally {
            bus2.forEach { it.sourceReportTs = sourceAt.getValue(it.id) }
            vehiclePredictions.saveAll(bus2)
        }
    }

    @Test
    fun `live source-age limit includes 600 seconds but excludes 601`() {
        val rows = vehiclePredictions.findAll().filter { it.vehicleId == "bus-2" }
        val original = rows.associate { it.id to it.sourceReportTs }
        try {
            rows.forEach { it.sourceReportTs = clock.instant().minusSeconds(600) }
            vehiclePredictions.saveAll(rows)
            assertThat(read.stopPredictions("a", "S3", "RA", 0).size).isEqualTo(2)
            rows.forEach { it.sourceReportTs = clock.instant().minusSeconds(601) }
            vehiclePredictions.saveAll(rows)
            assertThat(read.stopPredictions("a", "S3", "RA", 0).size).isEqualTo(1)
        } finally {
            rows.forEach { it.sourceReportTs = original.getValue(it.id) }
            vehiclePredictions.saveAll(rows)
        }
    }

    @Test
    fun `the stop board is sorted after deduplication regardless of vehicle id ordering`() {
        val rows = vehiclePredictions.findAll()
        val original = rows.map { Triple(it, it.vehicleId, it.predictedArrivalTs) }
        try {
            rows.forEach {
                val later = it.vehicleId == "bus-1"
                it.vehicleId = if (later) "a-later" else "z-earlier"
                it.predictedArrivalTs = clock.instant().plusSeconds(if (later) 600 else 60)
            }
            vehiclePredictions.saveAll(rows)
            val board = read.stopPredictions("a", "S3", "RA", 0)
            assertThat(board.map { it.predictedArrival }).isEqualTo(listOf(60L, 600L).map { clock.instant().plusSeconds(it).toString() })
            assertThat(headwayReadService.headway("a", "S3", "RA", 0).waitSec).isEqualTo(60)
        } finally {
            original.forEach { (row, vehicleId, arrival) ->
                row.vehicleId = vehicleId
                row.predictedArrivalTs = arrival
            }
            vehiclePredictions.saveAll(rows)
        }
    }

    @Test
    fun `a departed first stop is not an upcoming arrival and exposes crossing quality`() {
        val example = vehiclePredictions.findAll().first { it.vehicleId == "bus-1" }
        val row = vehiclePredictions.save(
            VehiclePrediction(
                feedCode = "a", vehicleId = "bus-1", tripRowId = example.tripRowId, blockPk = example.blockPk,
                tripPatternId = example.tripPatternId, stopPathIndex = 0, algorithm = example.algorithm,
                predictedArrivalTs = clock.instant().plusSeconds(60), predictedDepartureTs = clock.instant().plusSeconds(90),
                actualArrivalTs = null, actualDepartureTs = clock.instant().minusSeconds(10), confidenceSec = null,
                computedAt = clock.instant(), sourceReportTs = clock.instant(), serviceDate = example.serviceDate,
                actualReportGapSec = 180,
            ),
        )
        try {
            assertThat(read.stopPredictions("a", "S1", "RA", 0)).isEmpty()
            val actual = read.vehiclePredictions("a", "bus-1").single { it.stopPathIndex == 0 }
            assertThat(actual.actualReliable).isEqualTo(false)
            assertThat(actual.actualReportGapSec).isEqualTo(180)
        } finally {
            vehiclePredictions.deleteById(row.id!!)
        }
    }

    @Test
    fun `accuracy pairs common forecasts, measures missing coverage and computes real percentiles`() {
        val feed = feeds.findById(feedId).orElseThrow()
        val configured = feed.predictionAlgorithms
        feed.predictionAlgorithms = listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE, PredictionAlgorithm.KALMAN)
        feeds.save(feed)

        fun sample(
            algorithm: PredictionAlgorithm,
            vehicle: String,
            elapsed: Long,
            error: Int?,
            lead: Int = 240,
            sourceLead: Int = lead,
        ): PredictionAccuracy {
            val actual = clock.instant().minusSeconds(elapsed)
            val confidence = if (algorithm == PredictionAlgorithm.KALMAN) {
                if (error == 30) 25 else 150
            } else {
                null
            }
            return PredictionAccuracy(
                feedCode = "a", vehicleId = vehicle, tripRowId = 1L, stopPathIndex = 2, algorithm = algorithm,
                predictedTs = error?.let { actual.minusSeconds(it.toLong()) }, actualTs = actual,
                errorSec = error, absErrorSec = error?.let { kotlin.math.abs(it) }, createdAt = clock.instant(),
                horizonSec = if (lead <= 300) 300 else 600, predictedAt = actual.minusSeconds(sourceLead.toLong()),
                producedAt = actual.minusSeconds(lead.toLong()), actualLeadSec = lead, confidenceSec = confidence,
                routeId = "RA", directionId = 0, dayType = DayType.WEEKDAY, timeBand = TimeBand.MORNING_PEAK,
            )
        }
        val inserted = predictionAccuracies.saveAll(
            listOf(
                sample(PredictionAlgorithm.SCHEDULE_ADHERENCE, "paired-1", 300, -10),
                sample(PredictionAlgorithm.KALMAN, "paired-1", 300, 30),
                sample(PredictionAlgorithm.SCHEDULE_ADHERENCE, "paired-2", 200, 20, lead = 480),
                sample(PredictionAlgorithm.KALMAN, "paired-2", 200, 50, lead = 480),
                sample(PredictionAlgorithm.SCHEDULE_ADHERENCE, "missing", 100, null),
                sample(PredictionAlgorithm.KALMAN, "missing", 100, 100),
                sample(PredictionAlgorithm.SCHEDULE_ADHERENCE, "unpaired", 50, 900, lead = 220, sourceLead = 240),
                sample(PredictionAlgorithm.KALMAN, "unpaired", 50, 900, lead = 220, sourceLead = 230),
                sample(PredictionAlgorithm.KALMAN, "legacy", 40, 10000).apply {
                    actualLeadSec = null
                    producedAt = null
                },
            ),
        )
        try {
            val summaries =
                read.predictionAccuracy("a", null, 7, "RA", 0, DayType.WEEKDAY, TimeBand.MORNING_PEAK).associateBy { it.algorithm }
            val schedule = summaries.getValue(PredictionAlgorithm.SCHEDULE_ADHERENCE)
            val kalman = summaries.getValue(PredictionAlgorithm.KALMAN)
            assertThat(schedule.eligibleCount).isEqualTo(3)
            assertThat(schedule.sampleCount).isEqualTo(2)
            assertThat(schedule.predictionCoverage).isEqualTo(2.0 / 3)
            assertThat(schedule.meanErrorSec).isEqualTo(5.0)
            assertThat(kalman.eligibleCount).isEqualTo(3)
            assertThat(kalman.sampleCount).isEqualTo(3)
            assertThat(kalman.pairedSampleCount).isEqualTo(2)
            assertThat(kalman.predictionCoverage).isEqualTo(1.0)
            assertThat(kalman.meanAbsErrorSec).isEqualTo(40.0)
            assertThat(kalman.p90AbsErrorSec).isEqualTo(48.0)
            assertThat(kalman.p95AbsErrorSec).isEqualTo(49.0)
            assertThat(kalman.confidenceCoverage).isEqualTo(2.0 / 3)
            assertThat(kalman.confidenceSampleCount).isEqualTo(3)
            assertThat(kalman.byHorizon.map { it.horizonSec }).isEqualTo(listOf(300, 600))
            assertThat(read.predictionAccuracy("a", null, 7, routeId = "other")).isEmpty()
            assertThat(read.predictionAccuracy("a", null, 7, dayType = DayType.SATURDAY)).isEmpty()
            assertThat(read.predictionAccuracy("a", null, 7, timeBand = TimeBand.NIGHT)).isEmpty()
        } finally {
            predictionAccuracies.deleteAllById(inserted.map { it.id!! })
            feed.predictionAlgorithms = configured
            feeds.save(feed)
        }
    }

    @Test
    fun `an unavailable forecast has zero coverage but no fabricated error statistics`() {
        val at = clock.instant()
        val row = predictionAccuracies.save(
            PredictionAccuracy(
                feedCode = "a", vehicleId = "missing", tripRowId = 1L, stopPathIndex = 2,
                algorithm = PredictionAlgorithm.SCHEDULE_ADHERENCE,
                predictedTs = null, actualTs = at.minusSeconds(30), errorSec = null, absErrorSec = null, createdAt = at,
                horizonSec = 300, predictedAt = at.minusSeconds(270), producedAt = at.minusSeconds(270), actualLeadSec = 240,
            ),
        )
        try {
            val summary = read.predictionAccuracy("a", null, 7).single()
            assertThat(summary.eligibleCount).isEqualTo(1)
            assertThat(summary.sampleCount).isEqualTo(0)
            assertThat(summary.predictionCoverage).isEqualTo(0.0)
            assertThat(summary.meanAbsErrorSec).isNull()
            assertThat(summary.p95AbsErrorSec).isNull()
            assertThat(summary.confidenceCoverage).isNull()
        } finally {
            predictionAccuracies.deleteById(row.id!!)
        }
    }
}
