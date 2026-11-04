package eu.transittrack.predict.api

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.bean.override.mockito.MockReset
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Point
import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.match.AvlAssignmentMode
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
import eu.transittrack.predict.model.KalmanTravelTimeStateRepository
import eu.transittrack.predict.model.PredictionAccuracy
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.predict.model.TravelTimeObservationRepository
import eu.transittrack.predict.model.VehiclePrediction
import eu.transittrack.predict.model.VehiclePredictionRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.ScheduleTimeTable
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "transittrack.avl.enabled=true",
        "transittrack.avl.match.match-interval-ms=3600000",
        "transittrack.predict.enabled=true",
        "transittrack.predict.run.interval-ms=3600000",
    ],
)
@Import(PredictionGraphQlTest.Stub::class)
@AutoConfigureHttpGraphQlTester
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PredictionGraphQlTest(
    @Autowired val tester: HttpGraphQlTester,
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
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val exposedDatabase: Database,
) : PostgresPerClassTest() {
    @MockitoBean(reset = MockReset.NONE)
    lateinit var clock: Clock

    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L

    private fun feedRow() = feeds.findById(feedId)!!

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
                        assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                    ),
                ).id!!

        val feed = feeds.findById(feedId)!!
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
            p: Point,
            at: Instant,
        ) {
            whenever(clock.instant()).thenReturn(at)
            reports.save(
                AvlReportRow(
                    feedCode = feed.code, vehicleId = "bus-1", vehicleLabel = null, ts = at,
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
        processor.processBatch()
        predictionProcessor.processBatch()

        insert(geom.line.pointAt(0.60 * len), ts(8, 12))
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
    fun `vehiclePredictions returns stop predictions with nested stop`() {
        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1") {
                         stopPathIndex predictedArrival algorithm stop { stopId }
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isNotEmpty()
        val first = rows.first()
        assertThat(first["algorithm"]).isEqualTo("SCHEDULE_ADHERENCE")
        @Suppress("UNCHECKED_CAST")
        assertThat((first["stop"] as Map<String, Any?>)["stopId"]).isNotNull()
    }

    @Test
    fun `vehiclePredictions schedules arrivals in the agency timezone`() {
        val state = vehicleStates.findByFeedCodeAndVehicleId(feedRow().code, "bus-1")!!
        val prediction =
            vehiclePredictions
                .findByVehicleTrip(feedRow().code, "bus-1", state.tripRowId!!)
                .first()
        val schedule =
            scheduleTimes
                .findByTripOrdered(state.revisionId!!, state.tripRowId!!)
                .first { it.stopPathIndex == prediction.stopPathIndex }
        // A GTFS time is a wall-clock time in the agency zone - also on a DST transition day.
        val warsaw = ZoneId.of("Europe/Warsaw")
        val expected =
            prediction
                .serviceDate!!
                .atTime(LocalTime.ofSecondOfDay(schedule.arrivalSec!!.toLong()))
                .atZone(warsaw)
                .toInstant()
                .toString()

        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1") {
                         stopPathIndex scheduledArrival
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        val row = rows.first { (it["stopPathIndex"] as Number).toInt() == prediction.stopPathIndex }
        assertThat(row["scheduledArrival"]).isEqualTo(expected)
    }

    @Test
    fun `vehiclePredictions with matching algorithm argument returns rows`() {
        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1", algorithm:SCHEDULE_ADHERENCE) {
                         algorithm
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isNotEmpty()
        assertThat(rows.first()["algorithm"]).isEqualTo("SCHEDULE_ADHERENCE")
    }

    @Test
    fun `vehiclePredictions with non-matching algorithm argument returns no rows`() {
        val rows =
            tester
                .document(
                    """{ vehiclePredictions(feedCode:"a", vehicleId:"bus-1", algorithm:KALMAN) {
                         algorithm
                       } }""",
                ).execute()
                .path("vehiclePredictions")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isEmpty()
    }

    @Test
    fun `predictionAccuracy summarizes samples after a crossing`() {
        val rows =
            tester
                .document(
                    """{ predictionAccuracy(feedCode:"a") {
                         algorithm sampleCount meanAbsErrorSec byHorizon { horizonSec sampleCount meanAbsErrorSec }
                       } }""",
                ).execute()
                .path("predictionAccuracy")
                .entityList(Map::class.java)
                .get()

        assertThat(rows).isNotEmpty()
        val sampleCount = (rows.first()["sampleCount"] as Number).toInt()
        assertThat(sampleCount).isGreaterThanOrEqualTo(1)
        @Suppress("UNCHECKED_CAST")
        val byHorizon = rows.first()["byHorizon"] as List<Map<String, Any>>
        assertThat(byHorizon).isNotEmpty()
        assertThat(byHorizon.sumOf { (it["sampleCount"] as Number).toInt() }).isEqualTo(sampleCount)
    }

    @Test
    fun `predictionAccuracy binds contextual filters and exposes coverage and quantiles`() {
        val rows = tester
            .document(
                """{ predictionAccuracy(feedCode:"a", routeId:"RA", directionId:0, dayType:WEEKDAY, timeBand:MORNING_PEAK) {
                 eligibleCount predictionCoverage pairedSampleCount p90AbsErrorSec p95AbsErrorSec confidenceSampleCount
               } }""",
            ).execute()
            .path("predictionAccuracy")
            .entityList(Map::class.java)
            .get()
        assertThat(rows).isNotEmpty()
        assertThat((rows.first()["predictionCoverage"] as Number).toDouble()).isEqualTo(1.0)
        assertThat(rows.first()["p95AbsErrorSec"]).isNotNull()
    }

    @Test
    fun stopPredictionExposesRangeAndLate() {
        withSmileRows { smile ->
            val target = smile.first { it.stopPathIndex > 0 }
            val stopId = stopPathsOfTrip().single { it.stopPathIndex == target.stopPathIndex }.stopId
            val rows =
                tester
                    .document(
                        """{ stopPredictions(feedCode:"a", stopId:"$stopId", routeId:"RA", directionId:0, algorithm:SMILE_GBM) {
                             stopPathIndex predictedArrival algorithm rangeLowSec rangeHighSec lateProbability late
                           } }""",
                    ).execute()
                    .path("stopPredictions")
                    .entityList(Map::class.java)
                    .get()

            val row = rows.single { it["predictedArrival"] == target.predictedArrivalTs.toString() }
            assertThat(row["algorithm"]).isEqualTo("SMILE_GBM")
            assertThat((row["rangeLowSec"] as Number).toInt()).isEqualTo(-60)
            assertThat((row["rangeHighSec"] as Number).toInt()).isEqualTo(120)
            assertThat((row["lateProbability"] as Number).toDouble()).isEqualTo(0.7)
            assertThat(row["late"]).isEqualTo(true)
        }
    }

    @Test
    fun tripLatenessReturnsNextStopAndTerminal() {
        withSmileRows { smile ->
            val paths = stopPathsOfTrip()
            val next = smile.minBy { it.stopPathIndex }
            val expectedTimepoint = paths.first { it.stopPathIndex >= next.stopPathIndex && it.scheduleAdherenceStop }
            val result =
                tester
                    .document(
                        """{ tripLateness(feedCode:"a", vehicleId:"bus-1", algorithm:SMILE_GBM) {
                             vehicleId algorithm
                             nextStop { stopPathIndex stop { stopId } predictedArrival scheduledArrival rangeLowSec rangeHighSec late lateProbability }
                             nextTimepoint { stopPathIndex stop { stopId } }
                             terminal { stopPathIndex stop { stopId } }
                           } }""",
                    ).execute()
                    .path("tripLateness")
                    .entity(Map::class.java)
                    .get()

            assertThat(result["vehicleId"]).isEqualTo("bus-1")
            assertThat(result["algorithm"]).isEqualTo("SMILE_GBM")
            @Suppress("UNCHECKED_CAST")
            val nextStop = result["nextStop"] as Map<String, Any?>
            assertThat((nextStop["stopPathIndex"] as Number).toInt()).isEqualTo(next.stopPathIndex)
            assertThat(nextStop["predictedArrival"]).isEqualTo(next.predictedArrivalTs.toString())
            assertThat(nextStop["scheduledArrival"]).isNotNull()
            @Suppress("UNCHECKED_CAST")
            assertThat((nextStop["stop"] as Map<String, Any?>)["stopId"]).isEqualTo(paths[next.stopPathIndex].stopId)
            if (next.stopPathIndex > 0) {
                assertThat((nextStop["rangeLowSec"] as Number).toInt()).isEqualTo(-60)
                assertThat(nextStop["late"]).isEqualTo(true)
            }
            @Suppress("UNCHECKED_CAST")
            val timepoint = result["nextTimepoint"] as Map<String, Any?>
            assertThat((timepoint["stopPathIndex"] as Number).toInt()).isEqualTo(expectedTimepoint.stopPathIndex)
            @Suppress("UNCHECKED_CAST")
            val terminal = result["terminal"] as Map<String, Any?>
            assertThat((terminal["stopPathIndex"] as Number).toInt()).isEqualTo(paths.last().stopPathIndex)
            @Suppress("UNCHECKED_CAST")
            assertThat((terminal["stop"] as Map<String, Any?>)["stopId"]).isEqualTo(paths.last().stopId)
        }
    }

    @Test
    fun tripLatenessScheduledArrivalFallsBackToTheDeparture() {
        withSmileRows {
            fun terminalScheduled() =
                tester
                    .document(
                        """{ tripLateness(feedCode:"a", vehicleId:"bus-1", algorithm:SMILE_GBM) { terminal { scheduledArrival } } }""",
                    ).execute()
                    .path("tripLateness.terminal.scheduledArrival")
                    .entity(String::class.java)
                    .get()

            val state = vehicleStates.findByFeedCodeAndVehicleId(feedRow().code, "bus-1")!!
            val terminal = scheduleTimes.findByTripOrdered(state.revisionId!!, state.tripRowId!!).last()
            val arrival = Instant.parse(terminalScheduled())
            val t = ScheduleTimeTable
            val atTerminal = { (t.tripId eq state.tripRowId!!) and (t.stopPathIndex eq terminal.stopPathIndex) }
            transaction(exposedDatabase) {
                t.update(atTerminal) {
                    it[t.arrivalSec] = null
                    it[t.departureSec] = terminal.arrivalSec!! + 60
                }
            }
            try {
                assertThat(Instant.parse(terminalScheduled())).isEqualTo(arrival.plusSeconds(60))
            } finally {
                transaction(exposedDatabase) {
                    t.update(atTerminal) {
                        it[t.arrivalSec] = terminal.arrivalSec
                        it[t.departureSec] = terminal.departureSec
                    }
                }
            }
        }
    }

    @Test
    fun tripLatenessNextTimepointFollowsTheScheduleFlag() {
        val nextIndex =
            tester
                .document("""{ tripLateness(feedCode:"a", vehicleId:"bus-1") { nextStop { stopPathIndex } } }""")
                .execute()
                .path("tripLateness.nextStop.stopPathIndex")
                .entity(Int::class.java)
                .get()
        val paths = stopPathsOfTrip()
        // The fixture's next stop is not a timepoint, so the next timepoint lies further on.
        assertThat(paths[nextIndex].scheduleAdherenceStop).isEqualTo(false)
        assertThat(nextIndex).isLessThan(paths.last().stopPathIndex)

        fun nextTimepoint() =
            tester
                .document("""{ tripLateness(feedCode:"a", vehicleId:"bus-1") { nextTimepoint { stopPathIndex } } }""")
                .execute()
                .path("tripLateness.nextTimepoint.stopPathIndex")
                .entity(Int::class.java)
                .get()

        assertThat(nextTimepoint()).isEqualTo(paths.first { it.stopPathIndex > nextIndex && it.scheduleAdherenceStop }.stopPathIndex)
        val flipped = paths[nextIndex].apply { scheduleAdherenceStop = true }
        stopPaths.save(flipped)
        try {
            assertThat(nextTimepoint()).isEqualTo(nextIndex)
        } finally {
            stopPaths.save(flipped.apply { scheduleAdherenceStop = false })
        }
    }

    @Test
    fun tripLatenessDefaultsToFirstConfiguredAlgorithmWithAnAssessment() {
        val feed = feedRow()
        val configured = feed.predictionAlgorithms
        feed.predictionAlgorithms = listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE, PredictionAlgorithm.SMILE_GBM)
        feeds.save(feed)
        try {
            withSmileRows {
                val algorithm =
                    tester
                        .document("""{ tripLateness(feedCode:"a", vehicleId:"bus-1") { algorithm } }""")
                        .execute()
                        .path("tripLateness.algorithm")
                        .entity(String::class.java)
                        .get()
                // SCHEDULE_ADHERENCE comes first but never assesses lateness.
                assertThat(algorithm).isEqualTo("SMILE_GBM")
            }
            val withoutAssessment =
                tester
                    .document("""{ tripLateness(feedCode:"a", vehicleId:"bus-1") { algorithm nextStop { late } } }""")
                    .execute()
                    .path("tripLateness")
                    .entity(Map::class.java)
                    .get()
            assertThat(withoutAssessment["algorithm"]).isEqualTo("SCHEDULE_ADHERENCE")
            @Suppress("UNCHECKED_CAST")
            assertThat((withoutAssessment["nextStop"] as Map<String, Any?>)["late"]).isNull()
        } finally {
            feed.predictionAlgorithms = configured
            feeds.save(feed)
        }
    }

    @Test
    fun tripLatenessIsNullWithoutPredictions() {
        tester
            .document("""{ tripLateness(feedCode:"a", vehicleId:"nobody") { vehicleId } }""")
            .execute()
            .path("tripLateness")
            .valueIsNull()
        tester
            .document("""{ tripLateness(feedCode:"a", vehicleId:"bus-1", algorithm:KALMAN) { vehicleId } }""")
            .execute()
            .path("tripLateness")
            .valueIsNull()
    }

    @Test
    fun accuracyExposesRangeCoverage() {
        val seeded =
            predictionAccuracies.saveAll(
                predictionAccuracies
                    .findAll()
                    .filter { it.feedCode == feedRow().code && it.algorithm == PredictionAlgorithm.SCHEDULE_ADHERENCE }
                    .map { smileAccuracy(it) },
            )
        assertThat(seeded).isNotEmpty()
        try {
            val rows =
                tester
                    .document(
                        """{ predictionAccuracy(feedCode:"a", algorithm:SMILE_GBM) {
                             algorithm sampleCount rangeCoverage rangeSampleCount byHorizon { rangeCoverage rangeSampleCount }
                           } }""",
                    ).execute()
                    .path("predictionAccuracy")
                    .entityList(Map::class.java)
                    .get()

            val row = rows.single()
            assertThat(row["algorithm"]).isEqualTo("SMILE_GBM")
            assertThat((row["rangeSampleCount"] as Number).toInt()).isEqualTo((row["sampleCount"] as Number).toInt())
            assertThat((row["rangeCoverage"] as Number).toDouble()).isEqualTo(1.0)
            @Suppress("UNCHECKED_CAST")
            val byHorizon = row["byHorizon"] as List<Map<String, Any?>>
            assertThat(byHorizon.sumOf { (it["rangeSampleCount"] as Number).toInt() })
                .isEqualTo((row["rangeSampleCount"] as Number).toInt())

            val withoutRange =
                tester
                    .document("""{ predictionAccuracy(feedCode:"a", algorithm:SCHEDULE_ADHERENCE) { rangeCoverage rangeSampleCount } }""")
                    .execute()
                    .path("predictionAccuracy")
                    .entityList(Map::class.java)
                    .get()
                    .single()
            assertThat((withoutRange["rangeSampleCount"] as Number).toInt()).isEqualTo(0)
            assertThat(withoutRange["rangeCoverage"]).isNull()
        } finally {
            predictionAccuracies.deleteAllById(seeded.map { it.id!! })
        }
    }

    private fun stopPathsOfTrip(): List<StopPath> {
        val state = vehicleStates.findByFeedCodeAndVehicleId(feedRow().code, "bus-1")!!
        return stopPaths.findByTripPatternOrdered(state.revisionId!!, state.tripPatternId!!)
    }

    /**
     * Copies bus-1's open SCHEDULE_ADHERENCE predictions as SMILE_GBM rows carrying a range and a
     * late assessment (none at stop path 0, a departure), and removes them once [block] returns.
     */
    private fun withSmileRows(block: (List<VehiclePrediction>) -> Unit) {
        val state = vehicleStates.findByFeedCodeAndVehicleId(feedRow().code, "bus-1")!!
        val open =
            vehiclePredictions
                .findByVehicleTrip(feedRow().code, "bus-1", state.tripRowId!!, PredictionAlgorithm.SCHEDULE_ADHERENCE)
                .filter { it.actualArrivalTs == null && it.actualDepartureTs == null }
        assertThat(open).isNotEmpty()
        val smile = vehiclePredictions.saveAll(open.map { smilePrediction(it) })
        try {
            block(smile)
        } finally {
            smile.forEach { vehiclePredictions.deleteById(it.id!!) }
        }
    }

    private fun smilePrediction(p: VehiclePrediction): VehiclePrediction {
        val assessed = p.stopPathIndex > 0
        return VehiclePrediction(
            feedCode = p.feedCode, vehicleId = p.vehicleId, tripRowId = p.tripRowId, blockPk = p.blockPk,
            tripPatternId = p.tripPatternId, stopPathIndex = p.stopPathIndex, algorithm = PredictionAlgorithm.SMILE_GBM,
            predictedArrivalTs = p.predictedArrivalTs, predictedDepartureTs = p.predictedDepartureTs,
            actualArrivalTs = null, actualDepartureTs = null, confidenceSec = p.confidenceSec, computedAt = p.computedAt,
            sourceReportTs = p.sourceReportTs, serviceDate = p.serviceDate,
            rangeLowSec = if (assessed) -60 else null, rangeHighSec = if (assessed) 120 else null,
            lateProbability = if (assessed) 0.7 else null, late = if (assessed) true else null,
        )
    }

    private fun smileAccuracy(a: PredictionAccuracy): PredictionAccuracy =
        PredictionAccuracy(
            feedCode = a.feedCode, vehicleId = a.vehicleId, tripRowId = a.tripRowId, stopPathIndex = a.stopPathIndex,
            algorithm = PredictionAlgorithm.SMILE_GBM, predictedTs = a.predictedTs, actualTs = a.actualTs,
            errorSec = a.errorSec, absErrorSec = a.absErrorSec, createdAt = a.createdAt, horizonSec = a.horizonSec,
            predictedAt = a.predictedAt, producedAt = a.producedAt, actualLeadSec = a.actualLeadSec,
            confidenceSec = a.confidenceSec, routeId = a.routeId, directionId = a.directionId, dayType = a.dayType,
            timeBand = a.timeBand, rangeLowSec = -86_400, rangeHighSec = 86_400,
        )
}
