package eu.transittrack.db

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import jakarta.persistence.EntityManager

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftEditTable
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.support.DstTimeZoneExtension
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: while JPA entities and Exposed tables describe the same rows, each must read what the
 * other wrote, at the current time and at an instant inside a DST fall-back hour. Deleted with the
 * JPA entities.
 */
@SpringBootTest
@ExtendWith(DstTimeZoneExtension::class)
class CrossReadGtfsMetaTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: org.jetbrains.exposed.v1.jdbc.Database,
) : PostgresPerMethodTest() {
    // Postgres keeps microseconds; the JVM clock may hand out nanoseconds.
    private var t0: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun feed() =
        GtfsFeed(
            code = "cross",
            name = "Cross",
            description = "d",
            url = "http://x/f.zip",
            pollingCron = "0 0 * * * *",
            enabled = false,
            autoActivate = true,
            source = FeedSource.CONFIG,
            createdAt = t0,
            updatedAt = t0.plusSeconds(1),
            lastIngestAt = t0.plusSeconds(2),
        )

    private fun revision(feedId: Long) =
        GtfsRevision(
            feedId = feedId,
            status = GtfsRevisionStatus.READY,
            sourceUrl = "http://x/f.zip",
            contentSha256 = "a".repeat(64),
            byteSize = 1234L,
            filesPresent = listOf("agency.txt", "stops.txt"),
            rowCounts = mapOf("gtfs_agency" to 1L, "gtfs_stop" to 99L),
            feedStartDate = LocalDate.of(2026, 1, 2),
            feedEndDate = LocalDate.of(2026, 12, 31),
            validationReport = """{"a": 1}""",
            errorMessage = "boom",
            createdAt = t0.plusSeconds(3),
            activatedAt = t0.plusSeconds(4),
            supersededAt = t0.plusSeconds(5),
            kind = DraftKind.DRAFT,
            baseRevisionId = null,
            label = "lbl",
            derivationStale = true,
            deriving = true,
            lastValidation = """{"publishable": true}""",
            editorClaimBy = "ed",
            editorClaimExpiresAt = t0.plusSeconds(6),
            version = 7,
            createdBy = "me",
        )

    private fun edit(revisionId: Long) =
        DraftEdit(
            revisionId = revisionId,
            seq = 3,
            op = "setTime",
            summary = "sum",
            forward = """{"f": 1}""",
            inverse = """{"i": 2}""",
            appliedAt = t0.plusSeconds(7),
            undone = true,
        )

    private fun assertSame(
        a: GtfsFeed,
        b: GtfsFeed,
    ) {
        assertThat(a.id).isEqualTo(b.id)
        assertThat(a.code).isEqualTo(b.code)
        assertThat(a.name).isEqualTo(b.name)
        assertThat(a.description).isEqualTo(b.description)
        assertThat(a.url).isEqualTo(b.url)
        assertThat(a.pollingCron).isEqualTo(b.pollingCron)
        assertThat(a.enabled).isEqualTo(b.enabled)
        assertThat(a.autoActivate).isEqualTo(b.autoActivate)
        assertThat(a.source).isEqualTo(b.source)
        assertThat(a.createdAt).isEqualTo(b.createdAt)
        assertThat(a.updatedAt).isEqualTo(b.updatedAt)
        assertThat(a.lastIngestAt).isEqualTo(b.lastIngestAt)
    }

    private fun assertSame(
        a: GtfsRevision,
        b: GtfsRevision,
    ) {
        assertThat(a.id).isEqualTo(b.id)
        assertThat(a.feedId).isEqualTo(b.feedId)
        assertThat(a.status).isEqualTo(b.status)
        assertThat(a.sourceUrl).isEqualTo(b.sourceUrl)
        assertThat(a.contentSha256).isEqualTo(b.contentSha256)
        assertThat(a.byteSize).isEqualTo(b.byteSize)
        assertThat(a.filesPresent).isEqualTo(b.filesPresent)
        assertThat(a.rowCounts).isEqualTo(b.rowCounts)
        assertThat(a.feedStartDate).isEqualTo(b.feedStartDate)
        assertThat(a.feedEndDate).isEqualTo(b.feedEndDate)
        assertThat(a.validationReport).isEqualTo(b.validationReport)
        assertThat(a.errorMessage).isEqualTo(b.errorMessage)
        assertThat(a.createdAt).isEqualTo(b.createdAt)
        assertThat(a.activatedAt).isEqualTo(b.activatedAt)
        assertThat(a.supersededAt).isEqualTo(b.supersededAt)
        assertThat(a.kind).isEqualTo(b.kind)
        assertThat(a.baseRevisionId).isEqualTo(b.baseRevisionId)
        assertThat(a.label).isEqualTo(b.label)
        assertThat(a.derivationStale).isEqualTo(b.derivationStale)
        assertThat(a.deriving).isEqualTo(b.deriving)
        assertThat(a.lastValidation).isEqualTo(b.lastValidation)
        assertThat(a.editorClaimBy).isEqualTo(b.editorClaimBy)
        assertThat(a.editorClaimExpiresAt).isEqualTo(b.editorClaimExpiresAt)
        assertThat(a.version).isEqualTo(b.version)
        assertThat(a.createdBy).isEqualTo(b.createdBy)
    }

    private fun assertSame(
        a: DraftEdit,
        b: DraftEdit,
    ) {
        assertThat(a.id).isEqualTo(b.id)
        assertThat(a.revisionId).isEqualTo(b.revisionId)
        assertThat(a.seq).isEqualTo(b.seq)
        assertThat(a.op).isEqualTo(b.op)
        assertThat(a.summary).isEqualTo(b.summary)
        assertThat(a.forward).isEqualTo(b.forward)
        assertThat(a.inverse).isEqualTo(b.inverse)
        assertThat(a.appliedAt).isEqualTo(b.appliedAt)
        assertThat(a.undone).isEqualTo(b.undone)
    }

    @Test
    fun feedRevisionDraftRoundTrip() = roundTrip()

    /** Every timestamp of the three rows falls inside the fall-back hour (t0 .. t0 + 7s). */
    @Test
    fun feedRevisionDraftRoundTripInDstFallBackHour() {
        t0 = DstTimeZoneExtension.DST_FALL_BACK
        roundTrip()
    }

    private fun roundTrip() {
        // JPA writes, Exposed reads.
        val f = feed()
        val r = revision(0)
        val e = edit(0)
        TransactionTemplate(txManager).executeWithoutResult {
            em.persist(f)
            r.feedId = f.id!!
            em.persist(r)
            e.revisionId = r.id!!
            em.persist(e)
            em.flush()
        }
        transaction(exposedDatabase) {
            assertSame(f, GtfsFeedTable.read(GtfsFeedTable.selectAll().where { GtfsFeedTable.id eq f.id!! }.single()))
            assertSame(r, GtfsRevisionTable.read(GtfsRevisionTable.selectAll().where { GtfsRevisionTable.id eq r.id!! }.single()))
            assertSame(e, DraftEditTable.read(DraftEditTable.selectAll().where { DraftEditTable.id eq e.id!! }.single()))
        }

        // Exposed writes, JPA reads.
        val f2 = feed().also { it.code = "cross2" }
        val r2 = revision(0)
        val e2 = edit(0)
        transaction(exposedDatabase) {
            f2.id = GtfsFeedTable.insert { GtfsFeedTable.bind(it, f2) }[GtfsFeedTable.id]
            r2.feedId = f2.id!!
            r2.id = GtfsRevisionTable.insert { GtfsRevisionTable.bind(it, r2) }[GtfsRevisionTable.id]
            e2.revisionId = r2.id!!
            e2.id = DraftEditTable.insert { DraftEditTable.bind(it, e2) }[DraftEditTable.id]
        }
        TransactionTemplate(txManager).executeWithoutResult {
            assertSame(f2, em.find(GtfsFeed::class.java, f2.id!!))
            assertSame(r2, em.find(GtfsRevision::class.java, r2.id!!))
            assertSame(e2, em.find(DraftEdit::class.java, e2.id!!))
        }
    }
}
