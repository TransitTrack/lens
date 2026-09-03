package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.GtfsProperties
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.ScheduleProperties

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GeoExtentProcessorTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val derivedGtfsWriter: DerivedGtfsWriter,
    @Autowired val jsonMapper: JsonMapper,
    @Autowired val stops: StopRepository,
    @Autowired val routes: RouteRepository,
    @Autowired val agencies: AgencyRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val shapePoints: ShapePointRepository,
) {
    private val context = DerivationContext()

    private fun stage1() =
        TripPatternProcessor(
            context,
            scheduleWriter,
            ScheduleProperties(),
            jsonMapper,
            routes,
            trips,
            stopTimes,
            stops,
            shapePoints,
        )

    private fun stage5() = GeoExtentProcessor(context, derivedGtfsWriter, routes)

    @Test
    fun `pattern, route and agency extents are computed`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                agency(rev, "A"),
                route(rev, "R1", "A"),
                route(rev, "R2", "A"),
                trip(rev, "R1", "T1"),
                trip(rev, "R2", "T2"),
                stop(rev, "a", 10.0, 20.0),
                stop(rev, "b", 11.0, 21.0),
                stop(rev, "c", 9.0, 19.0),
                stop(rev, "d", 15.0, 30.0),
                stopTime(rev, "T1", 1, "a", 3600),
                stopTime(rev, "T1", 2, "b", 4200),
                stopTime(rev, "T2", 1, "c", 3600),
                stopTime(rev, "T2", 2, "d", 4200),
            ),
        )
        stage1().postProcess(rev)
        stage5().postProcess(rev)

        val r2 = routes.findByRouteId(rev, "R2")!!
        assertThat(r2.extent!!.minLat).isEqualTo(9.0)
        assertThat(r2.extent!!.maxLat).isEqualTo(15.0)

        val a = agencies.findByAgencyId(rev, "A")!!
        assertThat(a.extent!!.minLat).isEqualTo(9.0)
        assertThat(a.extent!!.maxLon).isEqualTo(30.0)
    }

    @Test
    fun `a route whose trips are all skipped keeps a null extent`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                agency(rev, "A"),
                route(rev, "R", "A"),
                trip(rev, "R", "T"),
                stop(rev, "s1", 10.0, 20.0),
                stopTime(rev, "T", 1, "s1", 3600), // only one stop_time -> pattern skipped
            ),
        )
        stage1().postProcess(rev)
        stage5().postProcess(rev)

        assertThat(routes.findByRouteId(rev, "R")!!.extent).isNull()
    }

    @Test
    fun `onIngestionFailure clears extents and closes context`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                agency(rev, "A"),
                route(rev, "R1", "A"),
                trip(rev, "R1", "T1"),
                stop(rev, "a", 10.0, 20.0),
                stop(rev, "b", 11.0, 21.0),
                stopTime(rev, "T1", 1, "a", 3600),
                stopTime(rev, "T1", 2, "b", 4200),
            ),
        )
        stage1().postProcess(rev)
        stage5().postProcess(rev)

        stage5().onIngestionFailure(rev)

        assertThat(routes.findByRouteId(rev, "R1")!!.extent).isNull()

        var contextClosed = false
        try {
            context.get(rev)
        } catch (e: IllegalStateException) {
            contextClosed = true
        }
        assertThat(contextClosed).isEqualTo(true)
    }
}
