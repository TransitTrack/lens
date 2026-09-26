package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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
import eu.transittrack.gtfs.model.Frequency
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
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
@AutoConfigureJson
@EnableConfigurationProperties(GtfsProperties::class, ScheduleProperties::class)
@Import(StatelessSessionRevisionWriter::class, ScheduleWriter::class, DerivedGtfsWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BlockProcessorTest(
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
    @Autowired val blocks: BlockRepository,
    @Autowired val blockTrips: BlockTripRepository,
) : PostgresPerMethodTest() {
    private val context = DerivationContext()

    private fun stage1() = TripPatternProcessor(context, writer, ScheduleProperties(), json, routes, trips, stopTimes, stops, shapePoints)

    private fun stage2(props: ScheduleProperties = ScheduleProperties()) =
        SchedTripProcessor(context, writer, derivedGtfsWriter, props, routes, trips, frequencies)

    private fun stage3() = TravelTimesProcessor(context, writer)

    private fun stage4(props: ScheduleProperties = ScheduleProperties()) = BlockProcessor(context, writer, props, frequencies, stops)

    private fun run(
        rev: Long,
        props: ScheduleProperties = ScheduleProperties(),
    ): Map<String, Long> {
        stage1().postProcess(rev)
        stage2(props).postProcess(rev)
        stage3().postProcess(rev)
        return stage4(props).postProcess(rev)
    }

    @Test
    fun `scheduled block orders trips and computes layover`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"),
                trip(rev, "R", "T1", blockId = "B"), trip(rev, "R", "T2", blockId = "B"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T1", 1, "a", 28800), stopTime(rev, "T1", 2, "b", 30600),
                stopTime(rev, "T2", 1, "b", 31200), stopTime(rev, "T2", 2, "a", 33000),
            ),
        )
        val counts = run(rev)

        assertThat(counts["block"]).isEqualTo(1L)
        assertThat(counts["block_trip"]).isEqualTo(2L)
        val block = blocks.findByBlockId(rev, "B").single()
        assertThat(block.startTimeSec).isEqualTo(28800)
        assertThat(block.endTimeSec).isEqualTo(33000)
        assertThat(block.tripCount).isEqualTo(2)
        val bts = blockTrips.findByBlockIdOrdered(rev, block.id!!)
        val t1 = trips.findByTripId(rev, "T1")!!
        assertThat(bts.map { it.tripId }).isEqualTo(listOf(t1.id, trips.findByTripId(rev, "T2")!!.id))
        assertThat(bts[0].layoverAfterSec).isEqualTo(600)
        assertThat(bts[0].deadheadAfter).isEqualTo(false)

        // layover write-back: T1's pattern ends on a >= 60s block gap, so its last
        // stop path is marked a layover stop with a break time.
        val t1Paths = stopPaths.findByTripPatternOrdered(rev, t1.tripPatternId!!)
        assertThat(t1Paths.last().layoverStop).isTrue()
        assertThat(t1Paths.last().breakTimeSec).isNotNull()
    }

    @Test
    fun `inferred block links separate arrival and departure platforms of one terminal`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"),
                trip(rev, "R", "T1"), trip(rev, "R", "T2"),
                // b-arr and b-dep are ~33 m apart: one terminal, two stop_ids.
                stop(rev, "a", 51.10, 17.00), stop(rev, "b-arr", 51.1100, 17.00), stop(rev, "b-dep", 51.1103, 17.00),
                stopTime(rev, "T1", 1, "a", 28800), stopTime(rev, "T1", 2, "b-arr", 30600),
                stopTime(rev, "T2", 1, "b-dep", 31200), stopTime(rev, "T2", 2, "a", 33000),
            ),
        )
        run(rev)

        val block = blocks.findByBlockId(rev, "inferred:S:T1").single()
        assertThat(block.tripCount).isEqualTo(2)
        val bts = blockTrips.findByBlockIdOrdered(rev, block.id!!)
        assertThat(bts.map { it.tripId })
            .isEqualTo(listOf(trips.findByTripId(rev, "T1")!!.id, trips.findByTripId(rev, "T2")!!.id))
        assertThat(bts[0].deadheadAfter).isEqualTo(false)
    }

    @Test
    fun `inferred block does not link a bus route onto a tram route`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "BUS", "A"), route(rev, "TRAM", "A").apply { routeType = 0 },
                trip(rev, "BUS", "T1"), trip(rev, "TRAM", "T2"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T1", 1, "a", 28800), stopTime(rev, "T1", 2, "b", 30600),
                stopTime(rev, "T2", 1, "b", 31200), stopTime(rev, "T2", 2, "a", 33000),
            ),
        )
        run(rev)

        assertThat(blocks.findByBlockId(rev, "inferred:S:T1").single().tripCount).isEqualTo(1)
        assertThat(blocks.findByBlockId(rev, "inferred:S:T2").single().tripCount).isEqualTo(1)
    }

    @Test
    fun `frequency block spans the frequencies row`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "F", blockId = "BF"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "F", 1, "a", 3600), stopTime(rev, "F", 2, "b", 4200),
                Frequency(rev, "F", 21600, 36000, 600, null),
            ),
        )
        val counts = run(rev)

        assertThat(counts["block"]).isEqualTo(1L)
        assertThat(counts["block_trip"]).isEqualTo(1L)
        val block = blocks.findByRevisionId(rev).single()
        assertThat(block.blockId).isEqualTo("BF|F")
        assertThat(block.startTimeSec).isEqualTo(21600)
        assertThat(block.endTimeSec).isEqualTo(36000)
        assertThat(blockTrips.findByBlockIdOrdered(rev, block.id!!)).hasSize(1)
    }

    @Test
    fun `unscheduled block spans the service day when tolerated`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"), trip(rev, "R", "N", blockId = "BN"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTimeNoTimes(rev, "N", 1, "a"), stopTimeNoTimes(rev, "N", 2, "b"),
            ),
        )
        val props = ScheduleProperties(tolerateNoScheduleTrips = true)
        val counts = run(rev, props)

        assertThat(counts["block"]).isEqualTo(1L)
        val block = blocks.findByBlockId(rev, "BN").single()
        assertThat(block.startTimeSec).isEqualTo(0)
        assertThat(block.endTimeSec).isEqualTo(86_400)
        assertThat(blockTrips.findByBlockIdOrdered(rev, block.id!!)).hasSize(1)
    }

    @Test
    fun `onIngestionFailure wipes blocks and resets stop_path layover`() {
        val rev = newRevision(feeds, revisions)
        gtfsWriter.write(
            listOf(
                route(rev, "R", "A"),
                trip(rev, "R", "T1", blockId = "B"), trip(rev, "R", "T2", blockId = "B"),
                stop(rev, "a", 51.10, 17.00), stop(rev, "b", 51.11, 17.00),
                stopTime(rev, "T1", 1, "a", 28800), stopTime(rev, "T1", 2, "b", 30600),
                stopTime(rev, "T2", 1, "b", 31200), stopTime(rev, "T2", 2, "a", 33000),
            ),
        )
        run(rev)
        val tp = patterns.findByRouteId(rev, "R").first()

        stage4().onIngestionFailure(rev)

        assertThat(blocks.findByRevisionId(rev).toList()).isEqualTo(emptyList())
        assertThat(blockTrips.findByRevisionId(rev).toList()).isEqualTo(emptyList())
        val paths = stopPaths.findByTripPatternOrdered(rev, tp.id!!)
        assertThat(paths.first().layoverStop).isTrue()
        assertThat(paths.last().layoverStop).isFalse()
    }
}
