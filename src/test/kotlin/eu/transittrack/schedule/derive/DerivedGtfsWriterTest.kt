package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.store.StatelessSessionRevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
@Import(StatelessSessionRevisionWriter::class, DerivedGtfsWriter::class, ScheduleWriter::class)
class DerivedGtfsWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val trips: TripRepository,
    @Autowired val derived: DerivedGtfsWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val patterns: TripPatternRepository,
) : PostgresPerMethodTest() {
    @Test
    fun `clearTripDerivation nulls trips derived columns`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A"), trip(rev, "R", "T")))
        val id = trips.findByTripId(rev, "T")!!.id!!
        scheduleWriter.write(listOf(TripPattern(rev, "K", "R", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val tripPatternId = patterns.findByPatternKey(rev, "K")!!.id!!
        derived.applyTripDerivation(
            rev,
            listOf(TripDerivation(id, tripPatternId, 100, 200, true, true, "Loop")),
        )
        derived.clearTripDerivation(rev)
        val t = trips.findByTripId(rev, "T")!!
        assertThat(t.tripPatternId).isNull()
        assertThat(t.startTimeSec).isNull()
        assertThat(t.endTimeSec).isNull()
        assertThat(t.frequencyBased).isNull()
        assertThat(t.noSchedule).isNull()
    }
}
