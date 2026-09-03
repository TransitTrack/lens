package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.GtfsProperties
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
import eu.transittrack.schedule.ScheduleProperties
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SchedTripProcessorTest(
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
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val schedTrips: SchedTripRepository,
    @Autowired val scheduleTimes: ScheduleTimeRepository,
) {
    private val context = DerivationContext()

    private fun stage1() = TripPatternProcessor(context, writer, ScheduleProperties(), json, routes, trips, stopTimes, stops, shapePoints)

    private fun stage2(props: ScheduleProperties = ScheduleProperties()) =
        SchedTripProcessor(context, writer, derivedGtfsWriter, props, routes, trips, frequencies)

    private fun seedRoute(rev: Long) =
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "T"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T", 1, "a", 3600), stopTime(rev, "T", 2, "b", 4200),
            ),
        )

    @Test
    fun `builds sched_trip + schedule_time and links the raw trip`() {
        val rev = newRevision(feeds, revisions)
        seedRoute(rev)
        stage1().postProcess(rev)
        val counts = stage2().postProcess(rev)

        assertThat(counts["sched_trip"]).isEqualTo(1L)
        assertThat(counts["schedule_time"]).isEqualTo(2L)
        val st = schedTrips.findByTripId(rev, "T")!!
        assertThat(st.startTimeSec).isEqualTo(3600)
        assertThat(trips.findByTripId(rev, "T")!!.tripPatternId).isEqualTo(st.tripPatternId)
        assertThat(
            context
                .get(rev)
                .derivedTrips
                .single()
                .schedTripId,
        ).isEqualTo(st.id)
    }

    @Test
    fun `a frequency trip is 0-based`() {
        val rev = newRevision(feeds, revisions)
        seedRoute(rev)
        gtfsWriter.write(
            listOf(
                eu.transittrack.gtfs.model
                    .Frequency(rev, "T", 3600, 7200, 900, 0),
            ),
        )
        stage1().postProcess(rev)
        stage2().postProcess(rev)

        val st = schedTrips.findByTripId(rev, "T")!!
        assertThat(st.frequencyBased).isTrue()
        assertThat(st.startTimeSec).isEqualTo(0)
        assertThat(scheduleTimes.findBySchedTripOrdered(rev, st.id!!)[0].departureSec).isEqualTo(0)
    }

    @Test
    fun `timeless trip fails when tolerate is off`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "T"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTimeNoTimes(rev, "T", 1, "a"), stopTimeNoTimes(rev, "T", 2, "b"),
            ),
        )
        stage1().postProcess(rev)
        assertFailure { stage2(ScheduleProperties(tolerateNoScheduleTrips = false)).postProcess(rev) }
            .isInstanceOf<IllegalStateException>()
    }

    @Test
    fun `timeless trip tolerated becomes a no_schedule sched_trip`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "T", blockId = "B"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTimeNoTimes(rev, "T", 1, "a"), stopTimeNoTimes(rev, "T", 2, "b"),
            ),
        )
        stage1().postProcess(rev)
        stage2(ScheduleProperties(tolerateNoScheduleTrips = true)).postProcess(rev)
        val st = schedTrips.findByTripId(rev, "T")!!
        assertThat(st.noSchedule).isTrue()
        assertThat(st.startTimeSec).isEqualTo(0)
        assertThat(st.endTimeSec).isEqualTo(86_400)
        assertThat(scheduleTimes.findBySchedTripOrdered(rev, st.id!!)[0].arrivalSec).isNull()
    }
}
