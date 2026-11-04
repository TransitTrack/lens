package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.geo.Extent
import eu.transittrack.geo.Point
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.ExposedRevisionWriter
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
@Import(ExposedRevisionWriter::class, DerivedGtfsWriter::class, ScheduleWriter::class)
class DerivedGtfsWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val gtfsWriter: RevisionWriter,
    @Autowired val trips: TripRepository,
    @Autowired val derived: DerivedGtfsWriter,
    @Autowired val scheduleWriter: ScheduleWriter,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val routes: RouteRepository,
    @Autowired val agencies: AgencyRepository,
    @Autowired val txManager: PlatformTransactionManager,
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

    // Each DerivedGtfsWriter method commits on its own (as the stateless-session transaction it
    // replaced did): a caller whose own transaction fails afterwards must not take the write down with it.

    private class CallerFailed : RuntimeException("caller failed")

    private fun inFailingCaller(block: () -> Unit) {
        assertFailure {
            TransactionTemplate(txManager).executeWithoutResult {
                block()
                throw CallerFailed()
            }
        }.isInstanceOf(CallerFailed::class)
    }

    private val box: Extent = Extent.of(listOf(Point(45.70, 21.15), Point(45.80, 21.30)))

    private fun bounds(e: Extent?) = e?.let { listOf(it.minLat, it.maxLat, it.minLon, it.maxLon) }

    @Test
    fun `writesSurviveCallerFailure - applyRouteExtents`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A")))
        inFailingCaller { derived.applyRouteExtents(rev, mapOf("R" to box)) }
        assertThat(bounds(routes.findByRouteId(rev, "R")!!.extent)).isEqualTo(bounds(box))
    }

    @Test
    fun `writesSurviveCallerFailure - applyAgencyExtents`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(agency(rev, "A")))
        inFailingCaller { derived.applyAgencyExtents(rev, mapOf("A" to box)) }
        assertThat(bounds(agencies.findByAgencyId(rev, "A")!!.extent)).isEqualTo(bounds(box))
    }

    @Test
    fun `writesSurviveCallerFailure - clearExtents`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(agency(rev, "A"), route(rev, "R", "A")))
        derived.applyRouteExtents(rev, mapOf("R" to box))
        derived.applyAgencyExtents(rev, mapOf("A" to box))
        inFailingCaller { derived.clearExtents(rev) }
        assertThat(routes.findByRouteId(rev, "R")!!.extent).isNull()
        assertThat(agencies.findByAgencyId(rev, "A")!!.extent).isNull()
    }

    @Test
    fun `writesSurviveCallerFailure - applyTripDerivation`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A"), trip(rev, "R", "T", headsign = " ")))
        val id = trips.findByTripId(rev, "T")!!.id!!
        scheduleWriter.write(listOf(TripPattern(rev, "K", "R", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val tripPatternId = patterns.findByPatternKey(rev, "K")!!.id!!
        inFailingCaller { derived.applyTripDerivation(rev, listOf(TripDerivation(id, tripPatternId, 100, 200, true, false, "Loop"))) }
        val t = trips.findByTripId(rev, "T")!!
        assertThat(t.tripPatternId).isEqualTo(tripPatternId)
        assertThat(t.startTimeSec).isEqualTo(100)
        assertThat(t.endTimeSec).isEqualTo(200)
        assertThat(t.frequencyBased).isEqualTo(true)
        assertThat(t.noSchedule).isEqualTo(false)
        // A blank headsign is filled from the resolved one.
        assertThat(t.tripHeadsign).isEqualTo("Loop")
    }

    @Test
    fun `applyTripDerivation keeps a non-blank headsign`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A"), trip(rev, "R", "T", headsign = "Own")))
        val id = trips.findByTripId(rev, "T")!!.id!!
        scheduleWriter.write(listOf(TripPattern(rev, "K", "R", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val tripPatternId = patterns.findByPatternKey(rev, "K")!!.id!!
        derived.applyTripDerivation(rev, listOf(TripDerivation(id, tripPatternId, 100, 200, false, false, "Loop")))
        assertThat(trips.findByTripId(rev, "T")!!.tripHeadsign).isEqualTo("Own")
    }

    @Test
    fun `writesSurviveCallerFailure - clearTripDerivation`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(listOf(route(rev, "R", "A"), trip(rev, "R", "T")))
        val id = trips.findByTripId(rev, "T")!!.id!!
        scheduleWriter.write(listOf(TripPattern(rev, "K", "R", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val tripPatternId = patterns.findByPatternKey(rev, "K")!!.id!!
        derived.applyTripDerivation(rev, listOf(TripDerivation(id, tripPatternId, 100, 200, true, true, "Loop")))
        inFailingCaller { derived.clearTripDerivation(rev) }
        val t = trips.findByTripId(rev, "T")!!
        assertThat(t.tripPatternId).isNull()
        assertThat(t.startTimeSec).isNull()
    }
}
