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

@PostgresSliceTest
@Import(StatelessSessionRevisionWriter::class, DerivedGtfsWriter::class)
class DerivedGtfsWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val trips: TripRepository,
    @Autowired val derived: DerivedGtfsWriter,
) {
    @Test
    fun `clearTripDerivation nulls trips derived columns`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A"), trip(rev, "R", "T")))
        val id = trips.findByTripId(rev, "T")!!.id!!
        derived.applyTripDerivation(
            listOf(TripDerivation(id, 1L, 100, 200, false, false, "Loop")),
        )
        derived.clearTripDerivation(rev)
        val t = trips.findByTripId(rev, "T")!!
        assertThat(t.tripPatternId).isNull()
        assertThat(t.startTimeSec).isNull()
        assertThat(t.endTimeSec).isNull()
    }
}
