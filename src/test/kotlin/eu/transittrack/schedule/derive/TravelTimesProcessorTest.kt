package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
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
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TravelTimesProcessorTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val writer: ScheduleWriter,
    @Autowired val derivedGtfsWriter: DerivedGtfsWriter,
    @Autowired val json: JsonMapper,
    @Autowired val routes: RouteRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val stops: StopRepository,
    @Autowired val shapePoints: ShapePointRepository,
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val travelTimes: TravelTimesForStopPathRepository,
) {
    private val context = DerivationContext()

    private fun stage1() = TripPatternProcessor(context, writer, ScheduleProperties(), json, routes, trips, stopTimes, stops, shapePoints)

    private fun stage2() = SchedTripProcessor(context, writer, derivedGtfsWriter, ScheduleProperties(), routes, trips, frequencies)

    private fun stage3() = TravelTimesProcessor(context, writer)

    private fun seedRoute(rev: Long) =
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "T"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T", 1, "a", 3600), stopTime(rev, "T", 2, "b", 4200),
            ),
        )

    @Test
    fun `median travel time per pattern index and trip_count set`() {
        val rev = newRevision(feeds, revisions)
        seedRoute(rev)
        stage1().postProcess(rev)
        stage2().postProcess(rev)
        val counts = stage3().postProcess(rev)

        assertThat(counts["travel_times_for_stop_path"]).isEqualTo(2L)
        val tp = patterns.findByRouteId(rev, "R").single()
        val tt = travelTimes.findByTripPatternOrdered(rev, tp.id!!)
        assertThat(tt.map { it.travelTimeSec }).isEqualTo(listOf<Int?>(null, 600))
        assertThat(patterns.findById(tp.id!!).get().tripCount).isEqualTo(1)
    }

    @Test
    fun `onIngestionFailure wipes rows and resets trip_count`() {
        val rev = newRevision(feeds, revisions)
        seedRoute(rev)
        stage1().postProcess(rev)
        stage2().postProcess(rev)
        stage3().postProcess(rev)

        val tp = patterns.findByRouteId(rev, "R").single()
        stage3().onIngestionFailure(rev)

        assertThat(travelTimes.findByTripPatternOrdered(rev, tp.id!!)).isEqualTo(emptyList())
        assertThat(patterns.findById(tp.id!!).get().tripCount).isEqualTo(0)
    }
}
