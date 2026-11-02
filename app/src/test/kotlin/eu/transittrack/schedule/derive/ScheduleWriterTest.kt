package eu.transittrack.schedule.derive

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.support.PostgresPerMethodTest

/** Like IngestionServiceTest, ScheduleWriter commits on its own connection, so this must not run inside the @JdbcTest rollback transaction. */
@PostgresSliceTest
@Import(ScheduleWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleWriterTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val patterns: TripPatternRepository,
    @Autowired val stopPaths: StopPathRepository,
    @Autowired val blocks: BlockRepository,
    @Autowired val writer: ScheduleWriter,
    @Autowired val txManager: PlatformTransactionManager,
) : PostgresPerMethodTest() {
    private var rev: Long = 0
    private var feedId: Long = 0

    private fun seed() {
        val feed =
            feeds.save(
                GtfsFeed(
                    "sw",
                    "SW",
                    null,
                    "http://x/z.zip",
                    null,
                    true,
                    null,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        feedId = feed.id!!
        rev =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feedId,
                        status = GtfsRevisionStatus.READY,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
                ).id!!
    }

    @AfterEach
    fun cleanup() {
        writer.deleteForRevision(rev)
        revisions.findById(rev)?.let { revisions.delete(it) }
        feeds.deleteById(feedId)
    }

    @Test
    fun `write, aggregate-update, and delete round-trip`() {
        seed()
        val tp = TripPattern(rev, "K", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)
        writer.write(listOf(tp))
        val savedTp = patterns.findByPatternKey(rev, "K")!!
        val sp =
            StopPath(
                rev,
                savedTp.id!!,
                0,
                "S1",
                null,
                1,
                0.0,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
            )
        writer.write(listOf(sp))

        writer.applyTripPatternTripCount(mapOf(savedTp.id!! to 3))
        val spId = stopPaths.findByTripPatternOrdered(rev, savedTp.id!!).single().id!!
        writer.applyStopPathLayover(listOf(StopPathLayoverUpdate(spId, true, 300)))

        assertThat(patterns.findByPatternKey(rev, "K")!!.tripCount).isEqualTo(3)
        val reloaded = stopPaths.findByTripPatternOrdered(rev, savedTp.id!!).single()
        assertThat(reloaded.breakTimeSec).isEqualTo(300)
        assertThat(reloaded.layoverStop).isEqualTo(true)

        writer.deleteForRevision(rev)
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
        assertThat(stopPaths.findByTripPatternOrdered(rev, savedTp.id!!)).isEmpty()
    }

    @Test
    fun `deleteTables removes only the named tables`() {
        seed()
        val tp = TripPattern(rev, "K2", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)
        writer.write(listOf(tp))
        val savedTp = patterns.findByPatternKey(rev, "K2")!!
        writer.deleteTables(rev, "trip_patterns")
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
    }

    @Test
    fun `resetTripPatternTripCount zeroes the column`() {
        seed()
        writer.write(listOf(TripPattern(rev, "K3", "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)))
        val id = patterns.findByPatternKey(rev, "K3")!!.id!!
        writer.applyTripPatternTripCount(mapOf(id to 5))
        assertThat(patterns.findById(id)!!.tripCount).isEqualTo(5)
        writer.resetTripPatternTripCount(rev)
        assertThat(patterns.findById(id)!!.tripCount).isEqualTo(0)
    }

    @Test
    fun `write assigns ids to the rows derivation reads ids back from`() {
        seed()
        val tp = pattern("K")
        writer.write(listOf(tp))
        assertThat(tp.id).isEqualTo(patterns.findByPatternKey(rev, "K")!!.id)
        val sp = stopPath(tp.id!!)
        writer.write(listOf(sp))
        assertThat(sp.id).isEqualTo(stopPaths.findByTripPatternOrdered(rev, tp.id!!).single().id)
        val b = Block(rev, "B1", "WD", 0, 3600, 1, listOf("RA"))
        writer.write(listOf(b))
        assertThat(b.id).isEqualTo(blocks.findByBlockAndService(rev, "B1", "WD")!!.id)
    }

    /**
     * pgjdbc runs a JDBC batch as one single-row INSERT ... RETURNING per row and hands the keys
     * back in statement order; [ScheduleWriter.write] relies on that to set each row's id. Checked
     * against each row's natural key on a batch larger than any driver/Exposed chunking.
     */
    @Test
    fun `write assigns each row its own id across a large batch`() {
        seed()
        val tps = (0 until 300).map { pattern("K%03d".format(it)) }
        writer.write(tps)
        val idByKey = patterns.findByRevisionId(rev).associate { it.patternKey to it.id }
        assertThat(idByKey.size).isEqualTo(300)
        tps.forEach { assertThat(it.id, name = it.patternKey).isEqualTo(idByKey.getValue(it.patternKey)) }

        val paths = (0 until 300).map { i -> stopPath(tps[0].id!!).also { it.stopPathIndex = i } }
        writer.write(paths)
        val idByIndex = stopPaths.findByTripPatternOrdered(rev, tps[0].id!!).associate { it.stopPathIndex to it.id }
        paths.forEach { assertThat(it.id, name = "stop path ${it.stopPathIndex}").isEqualTo(idByIndex.getValue(it.stopPathIndex)) }
    }

    @Test
    fun `resetStopPathLayover sets layover_stop from the stop path index`() {
        seed()
        writer.write(listOf(pattern("K")))
        val tpId = patterns.findByPatternKey(rev, "K")!!.id!!
        writer.write(listOf(stopPath(tpId), stopPath(tpId).also { it.stopPathIndex = 1 }))
        val ids = stopPaths.findByTripPatternOrdered(rev, tpId).map { it.id!! }
        writer.applyStopPathLayover(ids.map { StopPathLayoverUpdate(it, true, 300) })
        writer.resetStopPathLayover(rev)
        val (first, second) = stopPaths.findByTripPatternOrdered(rev, tpId)
        assertThat(first.layoverStop).isEqualTo(true)
        assertThat(second.layoverStop).isEqualTo(false)
        assertThat(first.breakTimeSec).isNull()
        assertThat(second.breakTimeSec).isNull()
    }

    // Each ScheduleWriter method commits on its own (as the stateless-session transaction it replaced
    // did): a caller whose own transaction fails afterwards must not take the write down with it.

    private class CallerFailed : RuntimeException("caller failed")

    private fun inFailingCaller(block: () -> Unit) {
        assertFailure {
            TransactionTemplate(txManager).executeWithoutResult {
                block()
                throw CallerFailed()
            }
        }.isInstanceOf(CallerFailed::class)
    }

    private fun pattern(key: String) = TripPattern(rev, key, "RA", null, 0, "H", "SHP", 1, null, tripCount = 0)

    private fun stopPath(patternId: Long) = StopPath(rev, patternId, 0, "S1", null, 1, 0.0, null, null, null, false, false, false, null)

    @Test
    fun `writesSurviveCallerFailure - write`() {
        seed()
        inFailingCaller { writer.write(listOf(pattern("K"))) }
        assertThat(patterns.findByPatternKey(rev, "K")).isNotNull()
    }

    @Test
    fun `writesSurviveCallerFailure - deleteTables`() {
        seed()
        writer.write(listOf(pattern("K")))
        inFailingCaller { writer.deleteTables(rev, "trip_patterns") }
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
    }

    @Test
    fun `writesSurviveCallerFailure - deleteForRevision`() {
        seed()
        writer.write(listOf(pattern("K")))
        inFailingCaller { writer.deleteForRevision(rev) }
        assertThat(patterns.findByRevisionId(rev)).isEmpty()
    }

    @Test
    fun `writesSurviveCallerFailure - applyStopPathLayover`() {
        seed()
        writer.write(listOf(pattern("K")))
        val tpId = patterns.findByPatternKey(rev, "K")!!.id!!
        writer.write(listOf(stopPath(tpId)))
        val spId = stopPaths.findByTripPatternOrdered(rev, tpId).single().id!!
        inFailingCaller { writer.applyStopPathLayover(listOf(StopPathLayoverUpdate(spId, true, 300))) }
        val sp = stopPaths.findByTripPatternOrdered(rev, tpId).single()
        assertThat(sp.layoverStop).isEqualTo(true)
        assertThat(sp.breakTimeSec).isEqualTo(300)
    }

    @Test
    fun `writesSurviveCallerFailure - resetStopPathLayover`() {
        seed()
        writer.write(listOf(pattern("K")))
        val tpId = patterns.findByPatternKey(rev, "K")!!.id!!
        writer.write(listOf(stopPath(tpId)))
        val spId = stopPaths.findByTripPatternOrdered(rev, tpId).single().id!!
        writer.applyStopPathLayover(listOf(StopPathLayoverUpdate(spId, false, 300)))
        inFailingCaller { writer.resetStopPathLayover(rev) }
        val sp = stopPaths.findByTripPatternOrdered(rev, tpId).single()
        // reset sets layover_stop = (stop_path_index = 0); this path is index 0.
        assertThat(sp.layoverStop).isEqualTo(true)
        assertThat(sp.breakTimeSec).isNull()
    }

    @Test
    fun `writesSurviveCallerFailure - applyTripPatternTripCount`() {
        seed()
        writer.write(listOf(pattern("K")))
        val id = patterns.findByPatternKey(rev, "K")!!.id!!
        inFailingCaller { writer.applyTripPatternTripCount(mapOf(id to 4)) }
        assertThat(patterns.findById(id)!!.tripCount).isEqualTo(4)
    }

    @Test
    fun `writesSurviveCallerFailure - resetTripPatternTripCount`() {
        seed()
        writer.write(listOf(pattern("K")))
        val id = patterns.findByPatternKey(rev, "K")!!.id!!
        writer.applyTripPatternTripCount(mapOf(id to 4))
        inFailingCaller { writer.resetTripPatternTripCount(rev) }
        assertThat(patterns.findById(id)!!.tripCount).isEqualTo(0)
    }
}
