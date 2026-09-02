package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.config.GtfsProperties
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.config.ScheduleProperties
import eu.transittrack.schedule.model.TripPatternRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleDerivationExtentTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val derivedGtfsWriter: DerivedGtfsWriter,
    @Autowired val scheduleProps: ScheduleProperties,
    @Autowired val jsonMapper: JsonMapper,
    @Autowired val stops: StopRepository,
    @Autowired val routes: RouteRepository,
    @Autowired val agencies: AgencyRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val patterns: TripPatternRepository,
) {
    private fun service() = ScheduleDerivationService(
        stops, routes, trips, stopTimes, shapePoints, frequencies,
        scheduleWriter, derivedGtfsWriter, scheduleProps, jsonMapper,
    )

    @Test fun `pattern, route and agency extents are computed`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                agency(rev, "A"),
                route(rev, "R1", "A"), route(rev, "R2", "A"),
                trip(rev, "R1", "T1"), trip(rev, "R2", "T2"),
                stop(rev, "a", 10.0, 20.0), stop(rev, "b", 11.0, 21.0),
                stop(rev, "c", 9.0, 19.0), stop(rev, "d", 15.0, 30.0),
                stopTime(rev, "T1", 1, "a", 3600), stopTime(rev, "T1", 2, "b", 4200),
                stopTime(rev, "T2", 1, "c", 3600), stopTime(rev, "T2", 2, "d", 4200),
            ),
        )
        service().derive(rev)

        val p1 = patterns.findByRouteId(rev, "R1").single()
        assertEquals(10.0, p1.extent.minLat); assertEquals(11.0, p1.extent.maxLat)

        val r2 = routes.findByRouteId(rev, "R2")!!
        assertEquals(9.0, r2.extent!!.minLat); assertEquals(15.0, r2.extent!!.maxLat)

        val a = agencies.findByAgencyId(rev, "A")!!
        assertEquals(9.0, a.extent!!.minLat)
        assertEquals(30.0, a.extent!!.maxLon)

        // trips are back-linked to their pattern
        assertEquals(p1.id, trips.findByTripId(rev, "T1")!!.tripPatternId)
    }

    @Test fun `a route whose trips are all skipped keeps a null extent and its trip has no pattern link`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                agency(rev, "A"), route(rev, "R", "A"), trip(rev, "R", "T"),
                stop(rev, "s1", 10.0, 20.0),
                stopTime(rev, "T", 1, "s1", 3600),  // only one stop_time -> trip skipped
            ),
        )
        service().derive(rev)

        assertNull(routes.findByRouteId(rev, "R")!!.extent)
        assertNull(trips.findByTripId(rev, "T")!!.tripPatternId)
    }
}
