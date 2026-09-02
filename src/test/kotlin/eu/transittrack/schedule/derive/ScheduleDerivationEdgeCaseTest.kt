package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.config.GtfsProperties
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
import eu.transittrack.schedule.config.ScheduleProperties
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import java.time.Instant
import javax.sql.DataSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * Derivation edge cases that the happy-path `schedule-sample` fixture cannot reach,
 * kept on their own fixtures so the count/value assertions in
 * [ScheduleDerivationServiceTest] stay untouched:
 *
 * - `schedule-edge`: a trip with a dangling `stop_times.stop_id`, a frequency trip
 *   carrying a `block_id`, a shapeless trip, a stop far off its shape, two routes
 *   sharing one shape + stop list, and no `timepoint` column at all.
 * - `schedule-broken-times`: a trip whose last stop has neither arrival nor departure,
 *   which makes a real derivation throw partway through.
 */
@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, RevisionService::class, GtfsFeedService::class, ScheduleWriter::class, DerivedGtfsWriter::class)
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
    private fun ingest(fixture: String, deriveDuringPipeline: Boolean = false): Long {
        val f = feeds.save(
            GtfsFeed("edge", "EDGE", null, "http://x/g.zip", null, true, true,
                FeedSource.API, Instant.now(), Instant.now()),
        )
        feedId = f.id!!
        val ingestion = IngestionService(
            FixtureDownloader(fixture), revisionService, gtfsWriter, feeds, feedService,
            gtfsProps, shapes, GtfsFeedLoader(), SyncTaskExecutor(),
            if (deriveDuringPipeline) service() else mock<ScheduleDerivationService>(),
            ScheduleProperties(enabled = deriveDuringPipeline),
            scheduleWriter,
        )
        rev = ingestion.ingestBlocking("edge").id!!
        return rev
    }

    private fun service() = ScheduleDerivationService(
        stops, routes, trips, stopTimes, shapePoints, frequencies, scheduleWriter, derivedGtfsWriter, scheduleProps, jsonMapper,
    )

    private fun rowCount(table: String): Int =
        jdbc.queryForObject("select count(*) from $table where revision_id = ?", Int::class.java, rev)!!

    @AfterEach
    fun cleanup() {
        if (rev != 0L) {
            scheduleWriter.deleteForRevision(rev)
            revisions.findByFeedNewestFirst(feedId)
                .forEach { gtfsWriter.deleteAllForRevision(it.id!!); revisions.delete(it) }
        }
        if (feedId != 0L) feeds.deleteById(feedId)
        rev = 0; feedId = 0
    }

    // --- FIX 3: a trip referencing a stop with no resolvable coordinate is skipped ---

    @Test
    fun `a trip with a dangling stop reference is skipped, its siblings are not`() {
        ingest("schedule-edge")
        service().derive(rev)

        // E4 visits GHOST, which is not in stops.txt -> no sched_trip, no pattern from it.
        assertNull(schedTrips.findByTripId(rev, "E4"))
        assertNotNull(schedTrips.findByTripId(rev, "E5"))
        assertEquals(1, patterns.findByRouteId(rev, "RC").size)

        // No (0,0) coordinate leaked into any geometry or extent.
        for (p in patterns.findByRevisionId(rev)) {
            assertTrue(p.extent.minLat > 50.0, "pattern ${p.patternKey} minLat=${p.extent.minLat}")
            assertTrue(p.extent.minLon > 16.0, "pattern ${p.patternKey} minLon=${p.extent.minLon}")
            assertTrue(p.lengthM!! < 100_000.0, "pattern ${p.patternKey} lengthM=${p.lengthM}")
        }
    }

    // --- FIX 7: stop_times are batch-loaded per route, still sequenced per trip ---

    @Test
    fun `the batch stop_times finder returns each trip's rows in stop_sequence order`() {
        ingest("schedule-edge")

        // Derivation groups this one query's result by trip id and relies on each
        // sublist already being in stop_sequence order.
        val rows = stopTimes
            .findByTripIds(rev, listOf("E6", "E1", "E4"))
        assertEquals(listOf("E1", "E1", "E4", "E4", "E4", "E6", "E6", "E6"), rows.map { it.tripId })

        val byTrip = rows.groupBy { it.tripId }
        assertEquals(listOf("S1", "GHOST", "S4"), byTrip.getValue("E4").map { it.stopId })
        assertEquals(listOf(1, 2, 3), byTrip.getValue("E6").map { it.stopSequence })
        assertEquals(listOf("S1", "OFF", "S4"), byTrip.getValue("E6").map { it.stopId })
    }

    // --- FIX 5: frequency trips are not placed in blocks ---

    @Test
    fun `a frequency trip carrying a block_id is excluded from the block`() {
        ingest("schedule-edge")
        service().derive(rev)

        val blk = blocks.findByBlockAndService(rev, "BLK", "WK")!!
        assertEquals(2, blk.tripCount, "EF is frequency-based and must not be blocked")
        assertEquals(28800, blk.startTimeSec, "a 0-based frequency trip must not drag the block start to 0")
        assertEquals(34200, blk.endTimeSec)

        val ef = schedTrips.findByTripId(rev, "EF")!!
        assertTrue(ef.frequencyBased)
        assertNull(blockTrips.findBySchedTripId(rev, ef.id!!))

        // E1 -> E2 layover is measured against E2, not against the frequency trip.
        val e1 = schedTrips.findByTripId(rev, "E1")!!
        val e1bt = blockTrips.findBySchedTripId(rev, e1.id!!)!!
        assertEquals(0, e1bt.listIndex)
        assertEquals(32400 - 30600, e1bt.layoverAfterSec)   // E2 09:00 - E1 08:30
        val e2 = schedTrips.findByTripId(rev, "E2")!!
        assertEquals(1, blockTrips.findBySchedTripId(rev, e2.id!!)!!.listIndex)
    }

    // --- FIX 8: shapeless and off-shape stop paths fall back to straight lines ---

    @Test
    fun `a trip with no shape gets straight-line stop paths`() {
        ingest("schedule-edge")
        service().derive(rev)

        val e3 = schedTrips.findByTripId(rev, "E3")!!
        val pattern = patterns.findById(e3.tripPatternId).get()
        assertNull(pattern.shapeId)

        val paths = stopPaths.findByTripPatternOrdered(rev, pattern.id!!)
        assertEquals(2, paths.size)
        assertEquals(0.0, paths[0].lengthM)
        val expected = haversineMeters(51.100, 17.000, 51.120, 17.020)
        assertTrue(abs(paths[1].lengthM - expected) < 1.0, "lengthM=${paths[1].lengthM}, haversine=$expected")
        // A straight-line fallback geometry is exactly the two stop coordinates.
        assertEquals(
            listOf(listOf(17.000, 51.100), listOf(17.020, 51.120)),
            jsonMapper.readValue(paths[1].pathGeometry!!, List::class.java),
        )
    }

    @Test
    fun `a stop too far off its shape falls back to a straight line`() {
        ingest("schedule-edge")
        service().derive(rev)

        val e6 = schedTrips.findByTripId(rev, "E6")!!
        val pattern = patterns.findById(e6.tripPatternId).get()
        assertEquals("SHP_OUT", pattern.shapeId)

        val paths = stopPaths.findByTripPatternOrdered(rev, pattern.id!!)
        assertEquals(listOf("S1", "OFF", "S4"), paths.map { it.stopId })

        // OFF is ~700 m from SHP_OUT (> the 100 m default), so both of its segments are
        // straight lines between stop coordinates rather than shape slices.
        val s1ToOff = haversineMeters(51.100, 17.000, 51.115, 17.000)
        assertTrue(abs(paths[1].lengthM - s1ToOff) < 1.0, "lengthM=${paths[1].lengthM}, haversine=$s1ToOff")
        assertEquals(
            listOf(listOf(17.000, 51.100), listOf(17.000, 51.115)),
            jsonMapper.readValue(paths[1].pathGeometry!!, List::class.java),
        )
        val offToS4 = haversineMeters(51.115, 17.000, 51.130, 17.030)
        assertTrue(abs(paths[2].lengthM - offToS4) < 1.0, "lengthM=${paths[2].lengthM}, haversine=$offToS4")
    }

    @Test
    fun `without a timepoint column every timed stop is a wait stop`() {
        ingest("schedule-edge")
        service().derive(rev)

        // schedule-edge's stop_times.txt has no `timepoint` column at all, so the
        // fallback "has a time -> it is a timed stop" branch decides wait_stop.
        val e1 = schedTrips.findByTripId(rev, "E1")!!
        val paths = stopPaths.findByTripPatternOrdered(rev, e1.tripPatternId)
        assertTrue(paths.all { it.waitStop == true && it.scheduleAdherenceStop == true })
    }

    // --- FIX 9: pattern identity is route-scoped ---

    @Test
    fun `two routes sharing a shape and stop list get separate patterns`() {
        ingest("schedule-edge")
        service().derive(rev)

        // RA/E1 and RC/E5 both run SHP_OUT over exactly [S1, S4].
        val ra = patterns.findByRouteId(rev, "RA")
        val rc = patterns.findByRouteId(rev, "RC")
        assertEquals(1, ra.size)
        assertEquals(1, rc.size)
        assertTrue(ra.single().patternKey.startsWith("RA|SHP_OUT|S1_to_S4|"))
        assertTrue(rc.single().patternKey.startsWith("RC|SHP_OUT|S1_to_S4|"))
        assertTrue(ra.single().id != rc.single().id)
        // trip_count is not double-counted across the two routes.
        assertEquals(3, ra.single().tripCount)   // E1, E2, EF
        assertEquals(1, rc.single().tripCount)   // E5 (E4 skipped)
    }

    // --- FIX 2 / FIX 4: a real mid-derivation failure wipes every derived row ---

    @Test
    fun `a derivation that throws partway leaves zero rows in all five tables`() {
        ingest("schedule-broken-times")

        // Pass 1 writes and commits both of route RX's patterns and their stop paths
        // before pass 2 reaches X1, whose last stop has neither arrival nor departure.
        val e = assertFailsWith<IllegalStateException> { service().derive(rev) }
        assertTrue(e.message!!.contains("trip X1 (route RX)"), "message was: ${e.message}")
        assertTrue(e.message!!.contains("first and last stop must have a time"), "message was: ${e.message}")

        assertEquals(0, rowCount("trip_patterns"))
        assertEquals(0, rowCount("stop_path"))
        assertEquals(0, rowCount("sched_trip"))
        assertEquals(0, rowCount("schedule_time"))
        assertEquals(0, rowCount("block"))
    }

    @Test
    fun `a failing derivation in the pipeline fails the revision and leaves no derived rows`() {
        ingest("schedule-broken-times", deriveDuringPipeline = true)

        val loaded = revisions.findById(rev).get()
        assertEquals(GtfsRevisionStatus.FAILED, loaded.status)
        assertTrue(loaded.errorMessage!!.contains("trip X1"), "errorMessage was: ${loaded.errorMessage}")

        assertEquals(0, rowCount("trip_patterns"))
        assertEquals(0, rowCount("stop_path"))
        assertEquals(0, rowCount("sched_trip"))
        assertEquals(0, rowCount("schedule_time"))
        assertEquals(0, rowCount("block"))
    }

    // --- FIX 1: a pipeline failure AFTER a successful derivation still wipes schedule rows ---

    @Test
    fun `a failure after derivation succeeds still wipes the derived schedule rows`() {
        val f = feeds.save(
            GtfsFeed("edge", "EDGE", null, "http://x/g.zip", null, true, true,
                FeedSource.API, Instant.now(), Instant.now()),
        )
        feedId = f.id!!

        // Derivation "succeeds" and leaves rows behind...
        val deriving = mock<ScheduleDerivationService>()
        whenever(deriving.derive(any())).thenAnswer { inv ->
            val id = inv.arguments[0] as Long
            scheduleWriter.write(
                listOf(
                    eu.transittrack.schedule.model.TripPattern(
                        revisionId = id, patternKey = "RA|-|S1_to_S2|000000000000", routeId = "RA",
                        routeShortName = null,
                        directionId = 0, headsign = null, shapeId = null, stopCount = 2,
                        lengthM = 10.0,
                        extent = eu.transittrack.Extent.of(
                            listOf(eu.transittrack.Point(51.0, 17.0), eu.transittrack.Point(51.1, 17.1)),
                        ),
                        tripCount = 0,
                    ),
                ),
            )
            mapOf("trip_patterns" to 1L)
        }
        // ...and the very next pipeline step (READY) blows up.
        val brittle = FailAtReady(revisions, gtfsWriter, gtfsProps, calendars, calendarDates, feedInfos)

        val ingestion = IngestionService(
            FixtureDownloader("schedule-broken-times"), brittle, gtfsWriter, feeds, feedService,
            gtfsProps, shapes, GtfsFeedLoader(), SyncTaskExecutor(),
            deriving, ScheduleProperties(enabled = true), scheduleWriter,
        )
        rev = ingestion.ingestBlocking("edge").id!!

        assertEquals(GtfsRevisionStatus.FAILED, revisions.findById(rev).get().status)
        assertEquals(0, rowCount("trip_patterns"), "schedule rows must not survive on a FAILED revision")
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
