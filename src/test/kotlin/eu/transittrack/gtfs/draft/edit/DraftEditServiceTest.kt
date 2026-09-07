package eu.transittrack.gtfs.draft.edit

import javax.sql.DataSource
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * Like [eu.transittrack.gtfs.draft.DraftServiceTest], the ingestion pieces and `RevisionWriter`
 * commit on their own connections, so this must not run inside a rollback transaction.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DraftEditServiceTest(
    @Autowired val svc: DraftEditService,
    @Autowired val drafts: DraftService,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val trips: TripRepository,
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val clean = mutableListOf<Long>()

    init {
        // Throwaway reversible builder for TEST_BUMP: shift the stop's arrival by the direction's delta.
        EditOpRegistry.register("TEST_BUMP") { ctx, dir ->
            {
                val tripId = dir.get("tripId").asString()
                val seq = dir.get("seq").asInt()
                val delta = dir.get("delta").asInt()
                val row = ctx.stopTimes.findByTripId(ctx.revisionId, tripId).single { it.stopSequence == seq }
                row.arrivalTime = (row.arrivalTime ?: 0) + delta
                ctx.stopTimes.save(row)
            }
        }
    }

    @AfterEach
    fun c() {
        clean.forEach {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
        clean.clear()
    }

    // A throwaway op just for testing the template — bump a stop's arrival by 60s; inverse restores it.
    private fun bumpOp(
        tripId: String,
        seq: Int,
    ) = { _: EditContext ->
        object : EditOp {
            override val op = "TEST_BUMP"

            override fun plan(c: EditContext): PlannedEdit {
                val row = c.stopTimes.findByTripId(c.revisionId, tripId).single { it.stopSequence == seq }
                val before = row.arrivalTime
                return PlannedEdit(
                    summary = "bump $tripId#$seq",
                    forward = c.json
                        .createObjectNode()
                        .put("tripId", tripId)
                        .put("seq", seq)
                        .put("delta", 60),
                    inverse =
                        c.json
                            .createObjectNode()
                            .put("tripId", tripId)
                            .put("seq", seq)
                            .put("delta", -60)
                            .put("was", before),
                    mutate = {
                        row.arrivalTime = (row.arrivalTime ?: 0) + 60
                        c.stopTimes.save(row)
                    },
                )
            }
        }
    }

    @Test
    fun `apply bumps version, marks stale and journals, then undo restores`() {
        val (feedCode, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val draft = drafts.fork(feedCode, null, "T", "alice")
        clean += draft.id!!
        drafts.claimEditor(draft.id!!, "alice")
        val anySt = stopTimes.findByRevisionId(draft.id!!).first()
        val tripId = anySt.tripId
        val seq = anySt.stopSequence
        val original = stopTimes.findByTripId(draft.id!!, tripId).single { it.stopSequence == seq }.arrivalTime

        val r1 = svc.apply(draft.id!!, "alice", draft.version) { ctx -> bumpOp(tripId, seq)(ctx) }
        assertThat(r1.draft.version).isEqualTo(draft.version + 1)
        assertThat(r1.draft.derivationStale).isEqualTo(true)
        assertThat(r1.canUndo).isEqualTo(true)
        assertThat(
            stopTimes.findByTripId(draft.id!!, tripId).single { it.stopSequence == seq }.arrivalTime,
        ).isEqualTo((original ?: 0) + 60)

        // stale version rejected (draft.version is the pre-apply value)
        assertFailure {
            svc.apply(draft.id!!, "alice", draft.version) { ctx -> bumpOp(tripId, seq)(ctx) }
        }.isInstanceOf(StaleDraftException::class)

        val r2 = svc.undo(draft.id!!, "alice", r1.draft.version)
        assertThat(
            stopTimes.findByTripId(draft.id!!, tripId).single { it.stopSequence == seq }.arrivalTime,
        ).isEqualTo(original)
        assertThat(r2.canRedo).isEqualTo(true)
    }
}
