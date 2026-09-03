package eu.transittrack.schedule.derive

import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.startsWith
import org.junit.jupiter.api.AfterEach
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.GtfsProperties
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FeedInfoRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.haversineMeters
import eu.transittrack.schedule.ScheduleProperties
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Derivation edge cases that the happy-path `schedule-sample` fixture cannot reach, kept on their own fixtures so the count/value assertions in the per-processor slice tests and [runAllStages] here stay untouched:
 *
 * - `schedule-edge`: a trip with a dangling `stop_times.stop_id`, a frequency trip carrying a `block_id`, a shapeless trip, a stop far off its shape, two routes sharing one shape + stop list, and no `timepoint` column at all.
 * - `schedule-broken-times`: a trip whose last stop has neither arrival nor departure, which makes a real derivation throw partway through.
 */
@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(
    StatelessSessionRevisionWriter::class,
    RevisionService::class,
    GtfsFeedService::class,
    ScheduleWriter::class,
    DerivedGtfsWriter::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleDerivationEdgeCaseTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val revisionService: RevisionService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val gtfsProps: GtfsProperties,
    @Autowired val scheduleProps: ScheduleProperties,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val derivedGtfsWriter: DerivedGtfsWriter,
    @Autowired val stops: StopRepository,
    @Autowired val routes: RouteRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val shapes: ShapeRepository,
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val schedTrips: SchedTripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
    @Autowired val blocks: BlockRepository,
    @Autowired val blockTrips: BlockTripRepository,
    @Autowired val dataSource: DataSource,
    @Autowired val jsonMapper: JsonMapper,
    @Autowired val calendars: CalendarRepository,
    @Autowired val calendarDates: CalendarDateRepository,
    @Autowired val feedInfos: FeedInfoRepository,
) {
    private var feedId: Long = 0
    private var rev: Long = 0

    private val jdbc by lazy { JdbcTemplate(dataSource) }

    /** Ingests [fixture] with schedule derivation switched OFF, so tests drive it explicitly. */
    private fun ingest(
        fixture: String,
        deriveDuringPipeline: Boolean = false,
    ): Long {
        val f =
            feeds.save(
                GtfsFeed(
                    "edge",
                    "EDGE",
                    null,
                    "http://x/g.zip",
                    null,
                    true,
                    true,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        feedId = f.id!!
        val ingestion =
            IngestionService(
                FixtureDownloader(fixture),
                revisionService,
                gtfsWriter,
                feeds,
                feedService,
                gtfsProps,
                shapes,
                GtfsFeedLoader(),
                SyncTaskExecutor(),
                mock<org.springframework.context.ApplicationEventPublisher>(),
                eu.transittrack.gtfs.support.postProcessors(
                    *(if (deriveDuringPipeline) stages().toTypedArray() else emptyArray()),
                ),
            )
        rev = ingestion.ingestBlocking("edge").id!!
        return rev
    }

    private val context = DerivationContext()

    private fun stages(props: ScheduleProperties = scheduleProps): List<eu.transittrack.gtfs.ingest.IngestionPostProcessor> =
        listOf(
            TripPatternProcessor(
                context, scheduleWriter, props, jsonMapper, routes, trips, stopTimes, stops, shapePoints,
            ),
            SchedTripProcessor(context, scheduleWriter, derivedGtfsWriter, props, routes, trips, frequencies),
            TravelTimesProcessor(context, scheduleWriter),
            BlockProcessor(context, scheduleWriter, props, frequencies),
            GeoExtentProcessor(context, derivedGtfsWriter, routes),
            DerivationFinalizeProcessor(context, scheduleWriter),
        )

    @Suppress("UNCHECKED_CAST")
    private fun runAllStages(
        rev: Long,
        props: ScheduleProperties = scheduleProps,
    ): Map<String, Long> {
        val counts = HashMap<String, Long>()
        stages(props).forEach { counts.putAll(it.postProcess(rev) as Map<String, Long>) }
        return counts
    }

    private fun rowCount(table: String): Int =
        jdbc.queryForObject(
            "select count(*) from $table where revision_id = ?",
            Int::class.java,
            rev,
        )!!

    @AfterEach
    fun cleanup() {
        if (rev != 0L) {
            scheduleWriter.deleteForRevision(rev)
            revisions.findByFeedNewestFirst(feedId).forEach {
                gtfsWriter.deleteAllForRevision(it.id!!)
                revisions.delete(it)
            }
        }
        if (feedId != 0L) feeds.deleteById(feedId)
        rev = 0
        feedId = 0
    }

    // --- FIX 3: a trip referencing a stop with no resolvable coordinate is skipped ---

    @Test
    fun `a trip with a dangling stop reference is skipped, its siblings are not`() {
        ingest("schedule-edge")
        runAllStages(rev)

        // E4 visits GHOST, which is not in stops.txt -> no sched_trip, no pattern from it.
        assertThat(schedTrips.findByTripId(rev, "E4")).isNull()
        assertThat(schedTrips.findByTripId(rev, "E5")).isNotNull()
        assertThat(patterns.findByRouteId(rev, "RC")).hasSize(1)

        // No (0,0) coordinate leaked into any geometry or extent.
        for (p in patterns.findByRevisionId(rev)) {
            assertThat(p.extent.minLat).isGreaterThan(50.0)
            assertThat(p.extent.minLon).isGreaterThan(16.0)
            assertThat(p.lengthM!!).isLessThan(100_000.0)
        }
    }

    // --- FIX 7: stop_times are batch-loaded per route, still sequenced per trip ---

    @Test
    fun `the batch stop_times finder returns each trip's rows in stop_sequence order`() {
        ingest("schedule-edge")

        // Derivation groups this one query's result by trip id and relies on each
        // sublist already being in stop_sequence order.
        val rows = stopTimes.findByTripIds(rev, listOf("E6", "E1", "E4"))
        assertThat(rows.map { it.tripId })
            .isEqualTo(listOf("E1", "E1", "E4", "E4", "E4", "E6", "E6", "E6"))

        val byTrip = rows.groupBy { it.tripId }
        assertThat(byTrip.getValue("E4").map { it.stopId }).isEqualTo(listOf("S1", "GHOST", "S4"))
        assertThat(byTrip.getValue("E6").map { it.stopSequence }).isEqualTo(listOf(1, 2, 3))
        assertThat(byTrip.getValue("E6").map { it.stopId }).isEqualTo(listOf("S1", "OFF", "S4"))
    }

    // --- FIX 5: frequency trips are not placed in blocks ---

    @Test
    fun `a frequency trip carrying a block_id gets its own synthetic block`() {
        ingest("schedule-edge")
        runAllStages(rev)

        val blk = blocks.findByBlockAndService(rev, "BLK", "WK")!!
        assertThat(blk.tripCount).isEqualTo(2) // EF is frequency-based: not in the scheduled block
        // a 0-based frequency trip must not drag the block start to 0
        assertThat(blk.startTimeSec).isEqualTo(28800)
        assertThat(blk.endTimeSec).isEqualTo(34200)

        // EF now gets its own synthetic frequency block.
        val efBlk = blocks.findByBlockAndService(rev, "BLK|EF", "WK")!!
        assertThat(efBlk.tripCount).isEqualTo(1)

        val ef = schedTrips.findByTripId(rev, "EF")!!
        assertThat(ef.frequencyBased).isTrue()
        assertThat(blockTrips.findBySchedTripId(rev, ef.id!!)).isNotNull()

        // E1 -> E2 layover is measured against E2, not against the frequency trip.
        val e1 = schedTrips.findByTripId(rev, "E1")!!
        val e1bt = blockTrips.findBySchedTripId(rev, e1.id!!)!!
        assertThat(e1bt.listIndex).isEqualTo(0)
        assertThat(e1bt.layoverAfterSec).isEqualTo(32400 - 30600) // E2 09:00 - E1 08:30
        val e2 = schedTrips.findByTripId(rev, "E2")!!
        assertThat(blockTrips.findBySchedTripId(rev, e2.id!!)!!.listIndex).isEqualTo(1)
    }

    // --- FIX 8: shapeless and off-shape stop paths fall back to straight lines ---

    @Test
    fun `a trip with no shape gets straight-line stop paths`() {
        ingest("schedule-edge")
        runAllStages(rev)

        val e3 = schedTrips.findByTripId(rev, "E3")!!
        val pattern = patterns.findById(e3.tripPatternId).get()
        assertThat(pattern.shapeId).isNull()

        val paths = stopPaths.findByTripPatternOrdered(rev, pattern.id!!)
        assertThat(paths).hasSize(2)
        assertThat(paths[0].lengthM).isEqualTo(0.0)
        val expected = haversineMeters(51.100, 17.000, 51.120, 17.020)
        assertThat(paths[1].lengthM).isCloseTo(expected, 1.0)
        // A straight-line fallback geometry is exactly the two stop coordinates.
        assertThat(jsonMapper.readValue(paths[1].pathGeometry!!, List::class.java))
            .isEqualTo(listOf(listOf(17.000, 51.100), listOf(17.020, 51.120)))
    }

    @Test
    fun `a stop too far off its shape falls back to a straight line`() {
        ingest("schedule-edge")
        runAllStages(rev)

        val e6 = schedTrips.findByTripId(rev, "E6")!!
        val pattern = patterns.findById(e6.tripPatternId).get()
        assertThat(pattern.shapeId).isEqualTo("SHP_OUT")

        val paths = stopPaths.findByTripPatternOrdered(rev, pattern.id!!)
        assertThat(paths.map { it.stopId }).isEqualTo(listOf("S1", "OFF", "S4"))

        // OFF is ~700 m from SHP_OUT (> the 100 m default), so both of its segments are
        // straight lines between stop coordinates rather than shape slices.
        val s1ToOff = haversineMeters(51.100, 17.000, 51.115, 17.000)
        assertThat(paths[1].lengthM).isCloseTo(s1ToOff, 1.0)
        assertThat(jsonMapper.readValue(paths[1].pathGeometry!!, List::class.java))
            .isEqualTo(listOf(listOf(17.000, 51.100), listOf(17.000, 51.115)))
        val offToS4 = haversineMeters(51.115, 17.000, 51.130, 17.030)
        assertThat(paths[2].lengthM).isCloseTo(offToS4, 1.0)
    }

    @Test
    fun `without a timepoint column every timed stop is a wait stop`() {
        ingest("schedule-edge")
        runAllStages(rev)

        // schedule-edge's stop_times.txt has no `timepoint` column at all, so the
        // fallback "has a time -> it is a timed stop" branch decides wait_stop.
        val e1 = schedTrips.findByTripId(rev, "E1")!!
        val paths = stopPaths.findByTripPatternOrdered(rev, e1.tripPatternId)
        assertThat(paths.all { it.waitStop == true && it.scheduleAdherenceStop == true }).isTrue()
    }

    // --- FIX 9: pattern identity is route-scoped ---

    @Test
    fun `two routes sharing a shape and stop list get separate patterns`() {
        ingest("schedule-edge")
        runAllStages(rev)

        // RA/E1 and RC/E5 both run SHP_OUT over exactly [S1, S4].
        val ra = patterns.findByRouteId(rev, "RA")
        val rc = patterns.findByRouteId(rev, "RC")
        assertThat(ra).hasSize(1)
        assertThat(rc).hasSize(1)
        assertThat(ra.single().patternKey).startsWith("RA|SHP_OUT|S1_to_S4|")
        assertThat(rc.single().patternKey).startsWith("RC|SHP_OUT|S1_to_S4|")
        assertThat(ra.single().id).isNotEqualTo(rc.single().id)
        // trip_count is not double-counted across the two routes.
        assertThat(ra.single().tripCount).isEqualTo(3) // E1, E2, EF
        assertThat(rc.single().tripCount).isEqualTo(1) // E5 (E4 skipped)
    }

    // --- Step 7 fixtures: dup stops / headsign fallbacks / no-schedule trips ---

    @Test
    fun `consecutive duplicate stops are collapsed into one stop path`() {
        ingest("schedule-dup-stops")
        runAllStages(rev)

        val t1 = schedTrips.findByTripId(rev, "T1")!!
        val pattern = patterns.findById(t1.tripPatternId).get()
        // stop_times visits S1,S2,S2,S3 -> the consecutive S2 dup is removed.
        assertThat(pattern.stopCount).isEqualTo(3)
        assertThat(stopPaths.findByTripPatternOrdered(rev, pattern.id!!).map { it.stopId })
            .isEqualTo(listOf("S1", "S2", "S3"))
    }

    @Test
    fun `a trip with no headsign anywhere falls back to Loop`() {
        ingest("schedule-no-headsign")
        runAllStages(rev)

        val t1 = schedTrips.findByTripId(rev, "T1")!!
        assertThat(t1.headsign).isEqualTo("Loop")
        assertThat(patterns.findById(t1.tripPatternId).get().headsign).isEqualTo("Loop")
    }

    @Test
    fun `a blank trip headsign falls back to the first stop headsign`() {
        ingest("schedule-stop-headsign")
        runAllStages(rev)

        val t1 = schedTrips.findByTripId(rev, "T1")!!
        assertThat(t1.headsign).isEqualTo("Downtown")
    }

    @Test
    fun `a no-schedule trip is rejected unless tolerated`() {
        ingest("schedule-noschedule")

        val failure = assertFailure { runAllStages(rev) }
        failure.isInstanceOf<IllegalStateException>()
        stages().forEach { runCatching { it.onIngestionFailure(rev) } }
        assertThat(schedTrips.findByTripId(rev, "T1")).isNull()
    }

    @Test
    fun `a tolerated no-schedule trip becomes a no_schedule sched_trip with a full-day block`() {
        ingest("schedule-noschedule")
        runAllStages(rev, scheduleProps.copy(tolerateNoScheduleTrips = true))

        val t1 = schedTrips.findByTripId(rev, "T1")!!
        assertThat(t1.noSchedule).isTrue()
        assertThat(t1.startTimeSec).isEqualTo(0)
        assertThat(t1.endTimeSec).isEqualTo(86400)

        val blk = blocks.findByBlockAndService(rev, "BLK", "WK")!!
        assertThat(blk.startTimeSec).isEqualTo(0)
        assertThat(blk.endTimeSec).isEqualTo(86400)
    }

    // --- FIX 2 / FIX 4: a real mid-derivation failure wipes every derived row ---

    @Test
    fun `a derivation that throws partway leaves zero rows in all derived tables`() {
        ingest("schedule-broken-times")

        // Pass 1 writes and commits both of route RX's patterns and their stop paths
        // before pass 2 reaches X1, whose last stop has neither arrival nor departure.
        val failure = assertFailure { runAllStages(rev) }
        failure.isInstanceOf<IllegalStateException>()
        failure.messageContains("trip X1 (route RX)")
        failure.messageContains("first and last stop must have a time")

        stages().forEach { runCatching { it.onIngestionFailure(rev) } }

        for (table in listOf(
            "trip_patterns", "stop_path", "sched_trip", "schedule_time",
            "block", "block_trip", "travel_times_for_stop_path",
        )) {
            assertThat(rowCount(table)).isEqualTo(0)
        }
        assertThat(context.size()).isEqualTo(0)
    }

    @Test
    fun `a failing derivation in the pipeline fails the revision and leaves no derived rows`() {
        ingest("schedule-broken-times", deriveDuringPipeline = true)

        val loaded = revisions.findById(rev).get()
        assertThat(loaded.status).isEqualTo(GtfsRevisionStatus.FAILED)
        assertThat(loaded.errorMessage).isNotNull().contains("trip X1")

        assertThat(rowCount("trip_patterns")).isEqualTo(0)
        assertThat(rowCount("stop_path")).isEqualTo(0)
        assertThat(rowCount("sched_trip")).isEqualTo(0)
        assertThat(rowCount("schedule_time")).isEqualTo(0)
        assertThat(rowCount("block")).isEqualTo(0)
    }

    // --- FIX 1: a pipeline failure AFTER a successful derivation still wipes schedule rows ---

    @Test
    fun `a failure after derivation succeeds still wipes the derived schedule rows`() {
        val f =
            feeds.save(
                GtfsFeed(
                    "edge",
                    "EDGE",
                    null,
                    "http://x/g.zip",
                    null,
                    true,
                    true,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        feedId = f.id!!

        // Derivation "succeeds" and leaves rows behind — and cleans them up on failure.
        val deriving =
            object : eu.transittrack.gtfs.ingest.IngestionPostProcessor {
                override fun postProcess(revisionId: Long): Any {
                    scheduleWriter.write(
                        listOf(
                            eu.transittrack.schedule.model.TripPattern(
                                revisionId = revisionId,
                                patternKey = "RA|-|S1_to_S2|000000000000",
                                routeId = "RA",
                                routeShortName = null,
                                directionId = 0,
                                headsign = null,
                                shapeId = null,
                                stopCount = 2,
                                lengthM = 10.0,
                                extent =
                                    eu.transittrack.Extent.of(
                                        listOf(
                                            eu.transittrack.Point(51.0, 17.0),
                                            eu.transittrack.Point(51.1, 17.1),
                                        ),
                                    ),
                                tripCount = 0,
                            ),
                        ),
                    )
                    return mapOf("trip_patterns" to 1L)
                }

                override fun onIngestionFailure(revisionId: Long) {
                    scheduleWriter.deleteForRevision(revisionId)
                }
            }
        // ...and the very next pipeline step (READY) blows up.
        val brittle = FailAtReady(revisions, gtfsWriter, gtfsProps, calendars, calendarDates, feedInfos)

        val ingestion =
            IngestionService(
                FixtureDownloader("schedule-broken-times"),
                brittle,
                gtfsWriter,
                feeds,
                feedService,
                gtfsProps,
                shapes,
                GtfsFeedLoader(),
                SyncTaskExecutor(),
                mock<org.springframework.context.ApplicationEventPublisher>(),
                eu.transittrack.gtfs.support
                    .postProcessors(deriving),
            )
        rev = ingestion.ingestBlocking("edge").id!!

        assertThat(revisions.findById(rev).get().status).isEqualTo(GtfsRevisionStatus.FAILED)
        // schedule rows must not survive on a FAILED revision
        assertThat(rowCount("trip_patterns")).isEqualTo(0)
    }

    /** A [RevisionService] that fails the pipeline step immediately after `DERIVING`. */
    private class FailAtReady(
        revisions: GtfsRevisionRepository,
        writer: RevisionWriter,
        props: GtfsProperties,
        calendars: CalendarRepository,
        calendarDates: CalendarDateRepository,
        feedInfos: FeedInfoRepository,
    ) : RevisionService(revisions, writer, props, calendars, calendarDates, feedInfos) {
        override fun transition(
            revisionId: Long,
            status: GtfsRevisionStatus,
            apply: (GtfsRevision) -> Unit,
        ): GtfsRevision {
            check(status != GtfsRevisionStatus.READY) { "post-derivation boom" }
            return super.transition(revisionId, status, apply)
        }
    }
}
