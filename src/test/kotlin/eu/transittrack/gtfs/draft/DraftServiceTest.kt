package eu.transittrack.gtfs.draft

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * `RevisionWriter` and the ingestion pieces commit on their own connections, so — like
 * [eu.transittrack.gtfs.revision.RevisionServiceTest] — this must not run inside a rollback
 * transaction. Hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftServiceTest(
    @Autowired val drafts: DraftService,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val stops: StopRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val cleanupRevs = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        for (id in cleanupRevs) runCatching { writer.deleteAllForRevision(id) }
        for (id in cleanupRevs) runCatching { revisions.deleteById(id) }
        runCatching { feeds.findByCode("schedule-sample")?.id?.let { feeds.deleteById(it) } }
        cleanupRevs.clear()
    }

    private fun stopIds(revisionId: Long): Set<Long> =
        jdbc.queryForList("select id from stops where revision_id = ?", Long::class.java, revisionId).filterNotNull().toSet()

    @Test
    fun `fork copies the active revision and isolates it`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanupRevs += activeRev
        val baseStops = stops.findByRevisionId(activeRev).size
        val basePatterns =
            jdbc.queryForObject(
                "select count(*) from trip_patterns where revision_id = ?",
                Long::class.java,
                activeRev,
            )!!
        assertThat(basePatterns).isGreaterThan(0L)

        val draft = drafts.fork(feedCode, null, "Proposal A", "alice")
        cleanupRevs += draft.id!!

        assertThat(draft.kind).isEqualTo(DraftKind.DRAFT)
        assertThat(draft.status).isEqualTo(GtfsRevisionStatus.DRAFT)
        assertThat(draft.baseRevisionId).isEqualTo(activeRev)
        assertThat(draft.derivationStale).isEqualTo(true)
        assertThat(stops.findByRevisionId(draft.id!!)).hasSize(baseStops)
        assertThat(draft.rowCounts["stops"]).isEqualTo(baseStops.toLong())

        // draft stop rows are physically new rows: PKs disjoint from the base's.
        val baseIds = stopIds(activeRev)
        val draftIds = stopIds(draft.id!!)
        assertThat(draftIds).hasSize(baseIds.size)
        assertThat(baseIds.intersect(draftIds)).isEqualTo(emptySet<Long>())

        // mutating a draft row does not touch the corresponding base row.
        val draftStop = jdbc.queryForMap("select id, stop_id from stops where revision_id = ? order by id limit 1", draft.id!!)
        val draftStopId = draftStop["id"] as Long
        val gtfsStopId = draftStop["stop_id"] as String
        val baseNameBefore =
            jdbc.queryForObject(
                "select stop_name from stops where revision_id = ? and stop_id = ?",
                String::class.java,
                activeRev,
                gtfsStopId,
            )
        jdbc.update("update stops set stop_name = ? where id = ?", "MUTATED-IN-DRAFT", draftStopId)
        val baseNameAfter =
            jdbc.queryForObject(
                "select stop_name from stops where revision_id = ? and stop_id = ?",
                String::class.java,
                activeRev,
                gtfsStopId,
            )
        assertThat(baseNameAfter).isEqualTo(baseNameBefore)

        // derived tables are NOT copied — the draft must be re-derived.
        val draftPatterns =
            jdbc.queryForObject(
                "select count(*) from trip_patterns where revision_id = ?",
                Long::class.java,
                draft.id!!,
            )
        assertThat(draftPatterns).isEqualTo(0L)
    }

    @Test
    fun `fork with unknown feed fails`() {
        assertFailure { drafts.fork("nope", null, null, null) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `listDrafts and get expose the fork`() {
        val (feedCode, activeRev) = ingestFactory.ingest("schedule-sample")
        cleanupRevs += activeRev

        val draft = drafts.fork(feedCode, null, "Proposal A", "alice")
        cleanupRevs += draft.id!!

        assertThat(drafts.listDrafts(null).map { it.id }).contains(draft.id)
        assertThat(drafts.listDrafts(null).first().id).isEqualTo(draft.id)

        assertThat(drafts.get(draft.id!!).id).isEqualTo(draft.id)
        assertFailure { drafts.get(activeRev) }.isInstanceOf<IllegalArgumentException>()
    }
}
