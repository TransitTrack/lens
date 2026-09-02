package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.gtfs.validate.GtfsFeedLoader
import eu.transittrack.schedule.config.ScheduleProperties
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, RevisionService::class, GtfsFeedService::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleDerivationServiceTest(
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
    @Autowired val jsonMapper: JsonMapper,
) {
    private var feedId: Long = 0
    private var rev: Long = 0

    private fun ingest(): Long {
        val f = feeds.save(GtfsFeed("sd", "SD", null, "http://x/g.zip", null, true, true,
            FeedSource.API, Instant.now(), Instant.now()))
        feedId = f.id!!
        val ingestion = IngestionService(
            FixtureDownloader("schedule-sample"), revisionService, gtfsWriter, feeds, feedService,
            gtfsProps, shapes, GtfsFeedLoader(), SyncTaskExecutor(),
            org.mockito.kotlin.mock<ScheduleDerivationService>(),
            ScheduleProperties(enabled = false),
            scheduleWriter,
        )
        rev = ingestion.ingestBlocking("sd").id!!
        return rev
    }

    private fun service() = ScheduleDerivationService(
        stops, routes, trips, stopTimes, shapePoints, frequencies, scheduleWriter, derivedGtfsWriter, scheduleProps, jsonMapper,
    )

    @AfterEach
    fun cleanup() {
        if (rev != 0L) {
            scheduleWriter.deleteForRevision(rev)
            revisions.findByFeedNewestFirst(feedId).forEach { gtfsWriter.deleteAllForRevision(it.id!!); revisions.delete(it) }
        }
        if (feedId != 0L) feeds.deleteById(feedId)
        rev = 0; feedId = 0
    }

    @Test
    fun `derives patterns, paths, trips and schedule times`() {
        ingest()
        val counts = service().derive(rev)

        assertEquals(4L, counts["trip_patterns"])       // A(T1,T2), B(T3), C(T4), D(T5)
        assertEquals(5L, counts["sched_trip"])
        assertEquals(4L + 3L + 4L + 4L + 2L, counts["schedule_time"])

        val all = patterns.findByRevisionId(rev)
        val patternA = all.first { it.stopCount == 4 && it.shapeId == "SHP_OUT" && it.directionId == 0 }
        assertEquals(2, schedTrips.findByTripPattern(rev, patternA.id!!).size)

        // T2 / S2 arrival is interpolated between 09:00:00 and 09:20:00
        val t2 = schedTrips.findByTripId(rev, "T2")!!
        val t2times = scheduleTimes.findBySchedTripOrdered(rev, t2.id!!)
        assertTrue(t2times[1].interpolated)
        assertTrue(t2times[1].arrivalSec!! in 32401..33599, "was ${t2times[1].arrivalSec}")

        // frequency-based T5: offset schedule, flagged
        val t5 = schedTrips.findByTripId(rev, "T5")!!
        assertTrue(t5.frequencyBased)
        assertEquals(0, t5.exactTimes)
        assertEquals(0, scheduleTimes.findBySchedTripOrdered(rev, t5.id!!)[0].departureSec)

        // geometry present on a shaped segment
        val paths = stopPaths.findByTripPatternOrdered(rev, patternA.id!!)
        assertEquals(0.0, paths[0].lengthM)
        assertTrue(paths[1].lengthM > 0.0)
        assertTrue(paths[1].pathGeometry!!.startsWith("[["))

        // idempotent
        val again = service().derive(rev)
        assertEquals(counts, again)
        assertEquals(4, patterns.findByRevisionId(rev).size)
    }

    @Test
    fun `derives blocks, block ordering, and pattern aggregates`() {
        ingest()
        val counts = service().derive(rev)
        assertEquals(2L, counts["block"])   // B1 (T1,T4), B2 (T2)

        val b1 = blocks.findByBlockAndService(rev, "B1", "WK")!!
        assertEquals(2, b1.tripCount)
        assertEquals(28800, b1.startTimeSec)
        assertEquals(33000, b1.endTimeSec)

        val t1bt = blockTrips.findBySchedTripId(rev, schedTrips.findByTripId(rev, "T1")!!.id!!)!!
        assertEquals(0, t1bt.listIndex)
        assertEquals(600, t1bt.layoverAfterSec)   // T4 08:40 - T1 08:30
        assertEquals(false, t1bt.deadheadAfter)   // both at S4
        val t4bt = blockTrips.findBySchedTripId(rev, schedTrips.findByTripId(rev, "T4")!!.id!!)!!
        assertEquals(1, t4bt.listIndex)

        // Pattern A trip_count and typical times
        val patternA = patterns.findByRevisionId(rev)
            .first { it.stopCount == 4 && it.shapeId == "SHP_OUT" && it.directionId == 0 }
        assertEquals(2, patternA.tripCount)
        val paths = stopPaths.findByTripPatternOrdered(rev, patternA.id!!)
        assertTrue(paths[1].typicalTravelTimeSec != null && paths[1].typicalTravelTimeSec!! > 0)
        // T1 is in block B1 with a 600 s layover -> last stop of Pattern A is a layover stop
        assertEquals(true, paths.last().layoverStop)
        assertTrue(paths.last().breakTimeSec != null)
    }
}
