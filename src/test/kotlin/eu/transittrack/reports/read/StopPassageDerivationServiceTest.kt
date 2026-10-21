package eu.transittrack.reports.read

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

/**
 * `schedule-sample` facts used here: trip T1 (route RA, service WK) has 4 scheduled stops
 * S1,S2,S3,S4 at stopPathIndex 0,1,2,3 (08:00/08:10/08:20/08:30 local). Each test below targets a
 * distinct service date so "one group per (tripRowId, serviceDate) trip execution" - the
 * derivation's grouping key - never accidentally merges two separate test scenarios together.
 *
 * - [completeDate]: bus-1 runs T1 end to end, stopping at all 4 scheduled stops.
 * - [partialDate]: bus-2 runs T1 but only ever reaches stopPathIndex 1, sending several repeated
 *   pings while lingering there before going silent - stopPathIndex 2 and 3 are never reached.
 * - [leadingStopDate]: bus-3's AVL coverage only starts once it's already at stopPathIndex 2 -
 *   stopPathIndex 0 and 1 must come back unreached, not inherit that first match.
 * - [crossDayQueryDate]/[crossDayServiceDate]: bus-4's ping ts falls inside the query window, but
 *   its inferred serviceDate ([crossDayServiceDate]) is the next day - its passages must not leak
 *   into a query for [crossDayQueryDate].
 */
@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = ["transittrack.avl.enabled=true"],
)
@Import(StopPassageDerivationServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StopPassageDerivationServiceTest(
    @Autowired val derivation: StopPassageDerivationService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
    @Autowired val factory: AvlMatchContextFactory,
    @Autowired val trips: TripRepository,
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val vehicleMatches: VehicleMatchRepository,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    private var feedId = 0L
    private val completeDate: LocalDate = LocalDate.of(2026, 9, 7)
    private val partialDate: LocalDate = LocalDate.of(2026, 9, 8)
    private val emptyDate: LocalDate = LocalDate.of(2026, 9, 9)
    private val leadingStopDate: LocalDate = LocalDate.of(2026, 9, 10)
    private val crossDayQueryDate: LocalDate = LocalDate.of(2026, 9, 11)
    private val crossDayServiceDate: LocalDate = crossDayQueryDate.plusDays(1)

    @BeforeAll
    fun setup() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")

        feedId =
            feeds
                .save(
                    AvlFeed(
                        code = "g", name = "G", gtfsFeedCode = "g", url = "x",
                        format = AvlFormat.GTFS_RT, pollIntervalSec = 15,
                        assignmentMode = AvlAssignmentMode.TRUST_DESCRIPTOR, enabled = true,
                        headers = null, source = AvlFeedSourceKind.CONFIG,
                        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
                    ),
                ).id!!

        val feed = feeds.findById(feedId).orElseThrow()
        val ctx = factory.open(feed)!!
        val t1 = trips.findByTripId(ctx.revisionId, "T1")!!

        fun ts(
            date: LocalDate,
            hour: Int,
            minute: Int,
        ): Instant = date.atTime(hour, minute).atZone(ctx.zone).toInstant()

        fun avlReport(
            vehicleId: String,
            at: Instant,
            descStartDate: LocalDate,
        ): Long =
            reports
                .save(
                    AvlReportRow(
                        feedCode = feed.code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                        lat = 51.1, lon = 17.0, bearing = null, speedMps = null, odometerM = null,
                        descTripId = "T1", descRouteId = "RA", descDirectionId = 0,
                        descStartDate = descStartDate, descStartTimeSec = null,
                        descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                        currentStatus = null, occupancyStatus = null, congestionLevel = null,
                        matchStatus = MatchStatus.PENDING, matchedAt = null, createdAt = at,
                    ),
                ).id!!

        fun insertMatch(
            vehicleId: String,
            serviceDate: LocalDate,
            at: Instant,
            stopPathIndex: Int,
            adherenceSec: Int?,
            descStartDate: LocalDate = serviceDate,
        ) {
            val reportId = avlReport(vehicleId, at, descStartDate)
            vehicleMatches.save(
                VehicleMatch(
                    avlReportId = reportId, feedCode = feed.code, vehicleId = vehicleId, ts = at,
                    revisionId = ctx.revisionId, tripRowId = t1.id!!, blockPk = null,
                    tripPatternId = t1.tripPatternId, stopPathIndex = stopPathIndex,
                    distanceAlongTripM = 0.0, deviationM = 0.0, scheduleAdherenceSec = adherenceSec,
                    snappedLat = 51.1, snappedLon = 17.0, heading = null, score = null,
                    createdAt = at,
                ),
            )
        }

        // bus-1 on T1, completeDate: completes the full run, stopping at every one of the 4
        // scheduled stops (stopPathIndex 0..3).
        insertMatch("bus-1", completeDate, ts(completeDate, 7, 59), 0, 60)
        insertMatch("bus-1", completeDate, ts(completeDate, 8, 9), 1, 30)
        insertMatch("bus-1", completeDate, ts(completeDate, 8, 19), 2, -10)
        insertMatch("bus-1", completeDate, ts(completeDate, 8, 29), 3, 0)

        // bus-2 on T1, partialDate: never gets past stopPathIndex 1 (vehicle dropped off AVL, or
        // trip abandoned) - it also sends several repeated pings while lingering at index 1,
        // which must collapse into a single observed passage per scheduled stop, not one per
        // ping.
        insertMatch("bus-2", partialDate, ts(partialDate, 7, 59), 0, 15)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 8), 1, 5)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 9), 1, 6)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 10), 1, 7)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 11), 1, 8)

        // bus-3 on T1, leadingStopDate: AVL coverage starts only once the vehicle is already at
        // stopPathIndex 2 (e.g. a late AVL start, or a noisy first ping that jumps ahead) -
        // stopPathIndex 0 and 1 must stay unreached (null), not inherit this first match's data.
        insertMatch("bus-3", leadingStopDate, ts(leadingStopDate, 8, 19), 2, -10)
        insertMatch("bus-3", leadingStopDate, ts(leadingStopDate, 8, 29), 3, 0)

        // bus-4 on T1: its single ping's ts falls inside `crossDayQueryDate`'s AVL fetch window,
        // but its `descStartDate` (and therefore its *inferred* serviceDate, which takes priority
        // in `inferServiceDate`) is the following day - simulating a trip execution whose
        // service-date grouping lands just outside the requested `[startDate, endDate]` range.
        insertMatch(
            "bus-4", crossDayServiceDate, ts(crossDayQueryDate, 23, 50), 1, 20,
            descStartDate = crossDayServiceDate,
        )
    }

    @AfterAll
    fun tearDown() {
        vehicleMatches.deleteAll()
        reports.deleteAll()
        feeds.deleteById(feedId)
    }

    @Test
    fun `derive emits one stop passage per scheduled stop for a trip that completed its run`() {
        val passages = derivation.derive("g", completeDate, completeDate, tripId = "T1")

        assertThat(passages).isEqualTo(passages.sortedBy { it.stopPathIndex })
        assertThat(passages.map { it.stopPathIndex }).isEqualTo(listOf(0, 1, 2, 3))
        assertThat(passages.map { it.serviceDate }).isEqualTo(List(4) { completeDate })
        // bus-1's seeded scheduleAdherenceSec per stopPathIndex 0..3 was 60, 30, -10, 0 - the
        // derived passage must reuse exactly that persisted value, not just be non-null.
        assertThat(passages.map { it.adherenceSec }).isEqualTo(listOf(60, 30, -10, 0))
    }

    @Test
    fun `derive marks a stop never reached as null observed and null adherence`() {
        val passages = derivation.derive("g", partialDate, partialDate, tripId = "T1")

        val lastStop = passages.single { it.stopPathIndex == 3 }
        assertThat(lastStop.observedSec).isNull()
        assertThat(lastStop.adherenceSec).isNull()

        val reachedStop = passages.single { it.stopPathIndex == 1 }
        assertThat(reachedStop.observedSec).isNotNull()
        assertThat(reachedStop.adherenceSec).isNotNull()
    }

    @Test
    fun `derive emits exactly one passage per stop when a vehicle sends repeated pings at the same stop path index`() {
        val passages = derivation.derive("g", partialDate, partialDate, tripId = "T1")

        val atIndexOne = passages.filter { it.stopPathIndex == 1 }
        assertThat(atIndexOne).hasSize(1)
        // Of bus-2's 4 repeated pings at stopPathIndex 1 (08:08/08:09/08:10/08:11, adherenceSec
        // 5/6/7/8), the first-by-ts one (08:08, adherenceSec 5) must be the one picked.
        assertThat(atIndexOne.single().adherenceSec).isEqualTo(5)
    }

    @Test
    fun `derive returns empty list when no VehicleMatch rows exist in range`() {
        val passages = derivation.derive("g", emptyDate, emptyDate, tripId = "T1")

        assertThat(passages).isEmpty()
    }

    @Test
    fun `derive throws for an unknown tripId in the active revision`() {
        assertFailure { derivation.derive("g", completeDate, completeDate, tripId = "does-not-exist") }
            .isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `derive marks stops before the first observed stopPathIndex as null, not inherited from that first match`() {
        val passages = derivation.derive("g", leadingStopDate, leadingStopDate, tripId = "T1")

        val beforeCoverage = passages.filter { it.stopPathIndex in setOf(0, 1) }
        assertThat(beforeCoverage).hasSize(2)
        beforeCoverage.forEach {
            assertThat(it.observedSec).isNull()
            assertThat(it.adherenceSec).isNull()
        }

        val withinCoverage = passages.filter { it.stopPathIndex in setOf(2, 3) }
        assertThat(withinCoverage).hasSize(2)
        withinCoverage.forEach {
            assertThat(it.observedSec).isNotNull()
            assertThat(it.adherenceSec).isNotNull()
        }
    }

    @Test
    fun `derive excludes a trip execution whose inferred service date falls outside the requested range`() {
        val passages = derivation.derive("g", crossDayQueryDate, crossDayQueryDate, tripId = "T1")

        assertThat(passages).isEmpty()
    }
}
