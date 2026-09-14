package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.GtfsProperties
import eu.transittrack.ScheduleProperties
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TripPatternProcessorTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val writer: ScheduleWriter,
    @Autowired val props: ScheduleProperties,
    @Autowired val json: JsonMapper,
    @Autowired val routes: RouteRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val stops: StopRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
) : PostgresPerMethodTest() {
    private val context = DerivationContext()

    private fun processor() = TripPatternProcessor(context, writer, props, json, routes, trips, stopTimes, stops, shapePoints)

    @Test
    fun `builds one pattern per route and collapses a duplicated stop`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R1", "A"),
                trip(rev, "R1", "T1"),
                stop(rev, "a", 51.10, 17.00),
                stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T1", 1, "a", 3600),
                stopTime(rev, "T1", 2, "a", 3660), // consecutive dup, differing time -> merged
                stopTime(rev, "T1", 3, "b", 4200),
            ),
        )
        val counts = processor().postProcess(rev)

        assertThat(counts["trip_patterns"]).isEqualTo(1L)
        assertThat(counts["stop_path"]).isEqualTo(2L) // a (merged) + b
        val tp = patterns.findByRouteId(rev, "R1").single()
        assertThat(stopPaths.findByTripPatternOrdered(rev, tp.id!!).map { it.stopId }).isEqualTo(listOf("a", "b"))
        // context populated for downstream stages
        assertThat(context.get(rev).cleanedRows.getValue("T1")).hasSize(2)
    }

    @Test
    fun `headsign falls back to Loop`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"),
                trip(rev, "R", "T"), // trip helper passes null headsign
                stop(rev, "a", 51.10, 17.00),
                stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T", 1, "a", 3600),
                stopTime(rev, "T", 2, "b", 4200),
            ),
        )
        processor().postProcess(rev)
        assertThat(patterns.findByRouteId(rev, "R").single().headsign).isEqualTo("Loop")
    }

    @Test
    fun `onIngestionFailure wipes its tables and closes the context`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "T"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T", 1, "a", 3600), stopTime(rev, "T", 2, "b", 4200),
            ),
        )
        val p = processor()
        p.postProcess(rev)
        p.onIngestionFailure(rev)
        assertThat(patterns.findByRevisionId(rev)).hasSize(0)
        assertThat(context.size()).isEqualTo(0)
    }
}
