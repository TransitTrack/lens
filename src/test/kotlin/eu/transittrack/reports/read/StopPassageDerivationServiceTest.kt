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
import eu.transittrack.avl.ingest.AvlOccupancyStatus
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
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerClassTest

/**
 * `schedule-sample` facts used here: trip T1 (route RA, service WK) has 4 scheduled stops
 * S1,S2,S3,S4 at stopPathIndex 0,1,2,3 (08:00/08:10/08:20/08:30 local). Each test below targets a
 * distinct service date so "one group per (tripRowId, serviceDate) trip execution" - the
 * derivation's grouping key - never accidentally merges two separate test scenarios together.
 *
 * **VehicleMatch.stopPathIndex semantics** (see [StopPassageDerivationService]'s KDoc for the full
 * derivation): a match whose reported `stopPathIndex == j` means the vehicle has *already arrived*
 * at stop `j - 1` and has *not yet* arrived at stop `j`. So "bus arrived at stop k" is represented
 * by a match with `stopPathIndex == k + 1`, never `stopPathIndex == k`.
 *
 * - [completeDate]: bus-1 runs T1 end to end, stopping at all 4 scheduled stops (so its 4 matches
 *   carry stopPathIndex 1,2,3,4 - one past each scheduled stop's own index 0,1,2,3).
 * - [partialDate]: bus-2 runs T1 but only ever reaches scheduled stop 1, sending several repeated
 *   pings (stopPathIndex 2, i.e. already past stop 1 and lingering, approaching stop 2) before going
 *   silent - scheduled stops 2 and 3 are never reached.
 * - [leadingStopDate]: bus-3's AVL coverage only starts once it's already arrived at scheduled stop
 *   1 (first match stopPathIndex 3, i.e. already past stop 2's predecessor) - scheduled stop 0 and 1
 *   must come back unreached (null), not inherit that first match.
 * - [crossDayQueryDate]/[crossDayServiceDate]: bus-4's ping ts falls inside the query window, but
 *   its inferred serviceDate ([crossDayServiceDate]) is the next day - its passages must not leak
 *   into a query for [crossDayQueryDate].
 * - [offByOneDate]: bus-5 regression test for the off-by-one bug itself - a ping that has merely
 *   left scheduled stop 0 (stopPathIndex 1, i.e. still approaching stop 1) must NOT be mistaken for
 *   the arrival at stop 1; only a later ping with stopPathIndex > 1 may be.
 * - [revisionDriftDate]: a trip matched under a non-active GTFS revision must still resolve its
 *   schedule/stop-path lookups against that trip's OWN revision, not the feed's current one.
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
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val gtfsFeeds: GtfsFeedRepository,
    @Autowired val gtfsRevisions: GtfsRevisionRepository,
    @Autowired val tripPatterns: TripPatternRepository,
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
    private val offByOneDate: LocalDate = LocalDate.of(2026, 9, 12)
    private val revisionDriftDate: LocalDate = LocalDate.of(2026, 9, 13)
    private val terminalDate: LocalDate = LocalDate.of(2026, 9, 14)
    private val terminalShortDate: LocalDate = LocalDate.of(2026, 9, 15)

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
        // T1's pattern total shape length - used below to build matches that land at the pattern's
        // last stop-path index (3) with a `distanceAlongTripM` near the end, the only way
        // StopPassageDerivationService's terminal-stop special case can confirm the vehicle reached
        // the final scheduled stop (SpatialMatcher.stopPathIndexAt never returns index 4 == n).
        val t1TotalLengthM = stopPaths.findByTripPatternOrdered(ctx.revisionId, t1.tripPatternId!!).sumOf { it.lengthM }

        fun ts(
            date: LocalDate,
            hour: Int,
            minute: Int,
        ): Instant = date.atTime(hour, minute).atZone(ctx.zone).toInstant()

        fun avlReport(
            vehicleId: String,
            at: Instant,
            descStartDate: LocalDate,
            descTripId: String = "T1",
            descRouteId: String = "RA",
            occupancyStatus: Int? = null,
        ): Long =
            reports
                .save(
                    AvlReportRow(
                        feedCode = feed.code, vehicleId = vehicleId, vehicleLabel = null, ts = at,
                        lat = 51.1, lon = 17.0, bearing = null, speedMps = null, odometerM = null,
                        descTripId = descTripId, descRouteId = descRouteId, descDirectionId = 0,
                        descStartDate = descStartDate, descStartTimeSec = null,
                        descScheduleRelationship = null, currentStopSequence = null, currentStopId = null,
                        currentStatus = null, occupancyStatus = occupancyStatus, congestionLevel = null,
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
            tripRowId: Long = t1.id!!,
            tripPatternId: Long? = t1.tripPatternId,
            revisionId: Long = ctx.revisionId,
            descTripId: String = "T1",
            descRouteId: String = "RA",
            distanceAlongTripM: Double = 0.0,
            occupancyStatus: Int? = null,
        ) {
            val reportId = avlReport(vehicleId, at, descStartDate, descTripId, descRouteId, occupancyStatus)
            vehicleMatches.save(
                VehicleMatch(
                    avlReportId = reportId, feedCode = feed.code, vehicleId = vehicleId, ts = at,
                    revisionId = revisionId, tripRowId = tripRowId, blockPk = null,
                    tripPatternId = tripPatternId, stopPathIndex = stopPathIndex,
                    distanceAlongTripM = distanceAlongTripM, deviationM = 0.0, scheduleAdherenceSec = adherenceSec,
                    snappedLat = 51.1, snappedLon = 17.0, heading = null, score = null,
                    createdAt = at,
                ),
            )
        }

        // bus-1 on T1, completeDate: completes the full run, arriving at every one of the 4
        // scheduled stops (stopPathIndex 0..3). Stops 0-2 are represented by matches reporting
        // stopPathIndex 1..3 (one past each scheduled stop's own index, per the "arrived at stop
        // (j-1)" semantics documented above). Stop 3 is the pattern's FINAL scheduled stop, so it
        // can't be represented that way at all - SpatialMatcher.stopPathIndexAt clamps to the
        // pattern's last stop-path index (3) and can never report 4 - so its arrival is instead
        // represented by a match AT index 3 whose distanceAlongTripM is at the pattern's total
        // shape length (see StopPassageDerivationService's terminal-stop special case).
        insertMatch("bus-1", completeDate, ts(completeDate, 7, 59), 1, 60)
        insertMatch("bus-1", completeDate, ts(completeDate, 8, 9), 2, 30)
        insertMatch("bus-1", completeDate, ts(completeDate, 8, 19), 3, -10)
        insertMatch(
            "bus-1", completeDate, ts(completeDate, 8, 29), 3, 0,
            distanceAlongTripM = t1TotalLengthM,
            occupancyStatus = AvlOccupancyStatus.FEW_SEATS_AVAILABLE.ordinal,
        )

        // bus-2 on T1, partialDate: never gets past scheduled stop 1 (vehicle dropped off AVL, or
        // trip abandoned) - it sends several repeated pings while lingering just past stop 1
        // (stopPathIndex 2, i.e. already arrived at scheduled stop 1, approaching stop 2), which
        // must collapse into a single observed passage per scheduled stop, not one per ping.
        insertMatch("bus-2", partialDate, ts(partialDate, 7, 59), 1, 15)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 8), 2, 5)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 9), 2, 6)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 10), 2, 7)
        insertMatch("bus-2", partialDate, ts(partialDate, 8, 11), 2, 8)

        // bus-3 on T1, leadingStopDate: AVL coverage starts only once the vehicle has already
        // arrived at scheduled stop 1 (first match stopPathIndex 3, i.e. already past stop 2's
        // predecessor) - scheduled stop 0 and 1 must stay unreached (null), not inherit this first
        // match's data.
        insertMatch("bus-3", leadingStopDate, ts(leadingStopDate, 8, 19), 3, -10)
        insertMatch(
            "bus-3", leadingStopDate, ts(leadingStopDate, 8, 29), 3, 0,
            distanceAlongTripM = t1TotalLengthM,
        )

        // bus-4 on T1: its single ping's ts falls inside `crossDayQueryDate`'s AVL fetch window,
        // but its `descStartDate` (and therefore its *inferred* serviceDate, which takes priority
        // in `inferServiceDate`) is the following day - simulating a trip execution whose
        // service-date grouping lands just outside the requested `[startDate, endDate]` range.
        insertMatch(
            "bus-4", crossDayServiceDate, ts(crossDayQueryDate, 23, 50), 2, 20,
            descStartDate = crossDayServiceDate,
        )

        // bus-5 on T1, offByOneDate: regression test for the off-by-one stop-passage bug itself.
        // The first ping (stopPathIndex 1) merely means the vehicle has left scheduled stop 0 and
        // is *approaching* scheduled stop 1 - it must NOT be picked as the observed arrival for
        // stop 1 (the old, buggy `>=` selection would have done exactly that). Only the second ping
        // (stopPathIndex 2, adherenceSec 42) actually represents arrival at stop 1.
        insertMatch("bus-5", offByOneDate, ts(offByOneDate, 8, 5), 1, 999)
        insertMatch("bus-5", offByOneDate, ts(offByOneDate, 8, 12), 2, 42)

        // revisionDriftDate: a synthetic trip ("T-OLD") belonging to a non-active GTFS revision
        // (`oldRevisionId`, distinct from the feed's currently-active `ctx.revisionId`), with its
        // own ScheduleTime/StopPath rows seeded under that SAME old revision. A VehicleMatch
        // against this trip's row id must still resolve those schedule/stop-path lookups via the
        // trip's own revision, not the feed's globally-active one - otherwise they'd silently
        // return zero rows and every passage would be lost.
        // A second, non-active `gtfs_revision` row for the same GTFS feed - satisfies the FK
        // `gtfs_*.revision_id -> gtfs_revision(id)` while remaining distinct from (and SUPERSEDED
        // relative to) the feed's currently-active revision (`ctx.revisionId`).
        val gtfsFeed = gtfsFeeds.findByCode("g")!!
        val oldRevisionId =
            gtfsRevisions
                .save(
                    GtfsRevision(
                        feedId = gtfsFeed.id!!,
                        status = GtfsRevisionStatus.SUPERSEDED,
                        sourceUrl = "http://x/g.zip",
                        createdAt = Instant.EPOCH,
                    ),
                ).id!!
        val oldPatternId =
            tripPatterns
                .save(
                    TripPattern(
                        revisionId = oldRevisionId, patternKey = "RX-old", routeId = "RX",
                        routeShortName = null, directionId = 0, headsign = null, shapeId = null,
                        stopCount = 2, lengthM = null, tripCount = 1,
                    ),
                ).id!!
        val oldTrip =
            trips.save(
                Trip(
                    revisionId = oldRevisionId, routeId = "RX", serviceId = "WK", tripId = "T-OLD",
                    tripHeadsign = null, tripShortName = null, directionId = 0, blockId = null,
                    shapeId = null, wheelchairAccessible = null, bikesAllowed = null,
                    tripPatternId = oldPatternId,
                ),
            )
        scheduleTimes.save(
            ScheduleTime(
                revisionId = oldRevisionId, tripId = oldTrip.id!!, stopPathIndex = 0,
                arrivalSec = 28800, departureSec = 28800, interpolated = false,
                schedTravelTimeSec = null, schedDwellTimeSec = null,
            ),
        )
        scheduleTimes.save(
            ScheduleTime(
                revisionId = oldRevisionId, tripId = oldTrip.id!!, stopPathIndex = 1,
                arrivalSec = 29400, departureSec = 29400, interpolated = false,
                schedTravelTimeSec = null, schedDwellTimeSec = null,
            ),
        )
        stopPaths.save(
            StopPath(
                revisionId = oldRevisionId, tripPatternId = oldPatternId, stopPathIndex = 0,
                stopId = "OLD-S0", routeId = null, stopSeq = 1, lengthM = 0.0, pathGeometry = null,
                pickupType = null, dropOffType = null, waitStop = false, scheduleAdherenceStop = true,
                layoverStop = false, breakTimeSec = null,
            ),
        )
        // OLD-S1's lengthM (500.0, unlike OLD-S0's placeholder 0.0) gives this synthetic pattern a
        // non-trivial total shape length, so the terminal-stop distance check below actually
        // discriminates between "merely arrived at stop 0, approaching stop 1" and "reached the end
        // of stop path 1" instead of being trivially satisfied by both (as it would be if every
        // stop path's lengthM were 0.0).
        val oldStop1LengthM = 500.0
        stopPaths.save(
            StopPath(
                revisionId = oldRevisionId, tripPatternId = oldPatternId, stopPathIndex = 1,
                stopId = "OLD-S1", routeId = null, stopSeq = 2, lengthM = oldStop1LengthM, pathGeometry = null,
                pickupType = null, dropOffType = null, waitStop = false, scheduleAdherenceStop = true,
                layoverStop = false, breakTimeSec = null,
            ),
        )
        insertMatch(
            "bus-old", revisionDriftDate, ts(revisionDriftDate, 7, 59), 1, 50,
            tripRowId = oldTrip.id!!, tripPatternId = oldPatternId, revisionId = oldRevisionId,
            descTripId = "T-OLD", descRouteId = "RX",
        )
        // oldTrip's pattern has only 2 stop paths (indices 0,1), so stop-path index 1 is itself the
        // pattern's LAST stop-path index - its arrival must be represented at index 1 (not the
        // impossible index 2), with distanceAlongTripM at the pattern's total length (OLD-S0's
        // 0.0 + OLD-S1's oldStop1LengthM).
        insertMatch(
            "bus-old", revisionDriftDate, ts(revisionDriftDate, 8, 9), 1, 20,
            tripRowId = oldTrip.id!!, tripPatternId = oldPatternId, revisionId = oldRevisionId,
            descTripId = "T-OLD", descRouteId = "RX",
            distanceAlongTripM = oldStop1LengthM,
        )

        // bus-6 on T1, terminalDate: regression test for the terminal-stop special case itself.
        // Reaches scheduled stops 0-2 normally, then a match AT the last stop-path index (3) but
        // only a few metres short of the pattern's total shape length (well within
        // TERMINAL_STOP_TOLERANCE_M) - this must be recognized as having reached the final
        // scheduled stop (index 3), not come back null.
        insertMatch("bus-6", terminalDate, ts(terminalDate, 7, 59), 1, 60)
        insertMatch("bus-6", terminalDate, ts(terminalDate, 8, 9), 2, 30)
        insertMatch("bus-6", terminalDate, ts(terminalDate, 8, 19), 3, -10)
        insertMatch(
            "bus-6", terminalDate, ts(terminalDate, 8, 29), 3, 5,
            distanceAlongTripM = t1TotalLengthM - 2.0,
        )

        // bus-7 on T1, terminalShortDate: a match AT the last stop-path index (3) but still far
        // short of the pattern's total shape length (well outside TERMINAL_STOP_TOLERANCE_M) must
        // NOT be mistaken for reaching the final scheduled stop - the vehicle has only just entered
        // the last stop path, not reached its end, so stop 3 must stay unreached (null).
        insertMatch("bus-7", terminalShortDate, ts(terminalShortDate, 8, 19), 3, -10)
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
        // schedule-sample's T1 visits S1..S4 ("Stop 1".."Stop 4") at stopPathIndex 0..3.
        assertThat(passages.map { it.stopName }).isEqualTo(listOf("Stop 1", "Stop 2", "Stop 3", "Stop 4"))
        // bus-1's seeded scheduleAdherenceSec per stopPathIndex 0..3 was 60, 30, -10, 0 - the
        // derived passage must reuse exactly that persisted value, not just be non-null.
        assertThat(passages.map { it.adherenceSec }).isEqualTo(listOf(60, 30, -10, 0))
        // vehicleId/direction come from the trip execution's group key and T1 itself, so every
        // passage in the run shares the same value regardless of whether it was reached.
        assertThat(passages.map { it.vehicleId }).isEqualTo(List(4) { "bus-1" })
        assertThat(passages.map { it.direction }).isEqualTo(List(4) { 0 })
        // Only the terminal stop's observed ping carried an occupancyStatus; every other stop's
        // observed match left it unset.
        val terminalStop = passages.single { it.stopPathIndex == 3 }
        assertThat(terminalStop.occupancyStatus).isEqualTo(AvlOccupancyStatus.FEW_SEATS_AVAILABLE)
        assertThat(passages.filter { it.stopPathIndex != 3 }.map { it.occupancyStatus })
            .isEqualTo(listOf(null, null, null))
    }

    @Test
    fun `derive marks a stop never reached as null observed and null adherence`() {
        val passages = derivation.derive("g", partialDate, partialDate, tripId = "T1")

        val lastStop = passages.single { it.stopPathIndex == 3 }
        assertThat(lastStop.observedSec).isNull()
        assertThat(lastStop.adherenceSec).isNull()
        // vehicleId is the group's own key, not read off the (missing) observed match, so it's
        // still populated even for a stop the vehicle never reached.
        assertThat(lastStop.vehicleId).isEqualTo("bus-2")
        assertThat(lastStop.occupancyStatus).isNull()

        val reachedStop = passages.single { it.stopPathIndex == 1 }
        assertThat(reachedStop.observedSec).isNotNull()
        assertThat(reachedStop.adherenceSec).isNotNull()
    }

    @Test
    fun `derive emits exactly one passage per stop when a vehicle sends repeated pings at the same stop path index`() {
        val passages = derivation.derive("g", partialDate, partialDate, tripId = "T1")

        val atIndexOne = passages.filter { it.stopPathIndex == 1 }
        assertThat(atIndexOne).hasSize(1)
        // Of bus-2's 4 repeated pings reporting stopPathIndex 2 (08:08/08:09/08:10/08:11,
        // adherenceSec 5/6/7/8), the first-by-ts one (08:08, adherenceSec 5) must be the one picked
        // as the arrival at scheduled stop 1.
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

    @Test
    fun `derive does not attribute a still-approaching ping to the stop it has not yet arrived at`() {
        val passages = derivation.derive("g", offByOneDate, offByOneDate, tripId = "T1")

        // stopPathIndex 0 is correctly attributed bus-5's first ping (stopPathIndex 1, adherence
        // 999) - the ping that confirms arrival at stop 0.
        val stop0 = passages.single { it.stopPathIndex == 0 }
        assertThat(stop0.adherenceSec).isEqualTo(999)

        // stopPathIndex 1 must be attributed the SECOND ping (stopPathIndex 2, adherence 42) - the
        // one that actually confirms arrival at stop 1 - and NOT the first ping (stopPathIndex 1,
        // adherence 999), which only shows the vehicle approaching stop 1, not yet there. Under the
        // old, buggy `>=` selection this would incorrectly have come back as 999.
        val stop1 = passages.single { it.stopPathIndex == 1 }
        assertThat(stop1.adherenceSec).isEqualTo(42)

        // Stops 2 and 3 were never reached.
        val stop2 = passages.single { it.stopPathIndex == 2 }
        val stop3 = passages.single { it.stopPathIndex == 3 }
        assertThat(stop2.observedSec).isNull()
        assertThat(stop3.observedSec).isNull()
    }

    @Test
    fun `derive resolves schedule and stop-path lookups against the matched trip's own revision, not the feed's active one`() {
        val passages = derivation.derive("g", revisionDriftDate, revisionDriftDate)

        val oldTripPassages = passages.filter { it.tripId == "T-OLD" }
        assertThat(oldTripPassages).hasSize(2)

        val stop0 = oldTripPassages.single { it.stopPathIndex == 0 }
        assertThat(stop0.stopId).isEqualTo("OLD-S0")
        assertThat(stop0.adherenceSec).isEqualTo(50)

        val stop1 = oldTripPassages.single { it.stopPathIndex == 1 }
        assertThat(stop1.stopId).isEqualTo("OLD-S1")
        assertThat(stop1.adherenceSec).isEqualTo(20)
    }

    @Test
    fun `derive recognizes the pattern's final scheduled stop as reached via a clamped index plus near-end distance`() {
        val passages = derivation.derive("g", terminalDate, terminalDate, tripId = "T1")

        val lastStop = passages.single { it.stopPathIndex == 3 }
        assertThat(lastStop.observedSec).isNotNull()
        assertThat(lastStop.adherenceSec).isEqualTo(5)
    }

    @Test
    fun `derive does not treat a match still short of the pattern's end as reaching the final scheduled stop`() {
        val passages = derivation.derive("g", terminalShortDate, terminalShortDate, tripId = "T1")

        val lastStop = passages.single { it.stopPathIndex == 3 }
        assertThat(lastStop.observedSec).isNull()
        assertThat(lastStop.adherenceSec).isNull()
    }
}
