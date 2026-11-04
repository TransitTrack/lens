package eu.transittrack.gtfs.store

import java.time.Instant
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [ExposedRevisionWriter.deleteAllForRevision] commits in a transaction of its own, so this test must NOT run inside the default
 * `@JdbcTest` rollback transaction — otherwise the seeded `gtfs_revision` rows would be invisible to that transaction's
 * connection. Hence `NOT_SUPPORTED` + explicit cleanup in `@AfterEach`.
 */
@PostgresSliceTest
@Import(ExposedRevisionWriter::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExposedRevisionWriterTest(
    @Autowired val writer: ExposedRevisionWriter,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val routes: RouteRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) : PostgresPerMethodTest() {
    private var feedId = 0L
    private var rev1 = 0L
    private var rev2 = 0L

    /** Seed a real feed + revisions so the mandated FK on `revision_id` is satisfied (PF-11). */
    @BeforeEach
    fun seed() {
        feedId =
            feeds
                .save(
                    GtfsFeed(
                        code = "f1",
                        name = "F1",
                        description = null,
                        url = "http://x/z.zip",
                        pollingCron = null,
                        enabled = true,
                        autoActivate = null,
                        source = FeedSource.API,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                ).id!!
        rev1 =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feedId,
                        status = GtfsRevisionStatus.PENDING,
                        sourceUrl = "u",
                    ),
                ).id!!
        rev2 =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feedId,
                        status = GtfsRevisionStatus.PENDING,
                        sourceUrl = "u",
                    ),
                ).id!!
    }

    @AfterEach
    fun cleanup() {
        writer.deleteAllForRevision(rev1)
        writer.deleteAllForRevision(rev2)
        listOf(rev1, rev2).forEach(revisions::deleteById)
        feeds.deleteById(feedId)
    }

    private fun route(
        rev: Long,
        id: String,
    ) = Route(
        revisionId = rev,
        routeId = id,
        agencyId = null,
        routeShortName = id,
        routeLongName = null,
        routeDesc = null,
        routeType = 3,
        routeUrl = null,
        routeColor = null,
        routeTextColor = null,
        routeSortOrder = null,
        continuousPickup = null,
        continuousDropOff = null,
        networkId = null,
    )

    @Test
    fun `writes a batch and deletes by revision`() {
        writer.write((1..250).map { route(rev1, "R$it") })
        assertThat(routes.findByRevisionId(rev1)).hasSize(250)

        writer.write(listOf(route(rev2, "X")))
        writer.deleteAllForRevision(rev1)

        assertThat(routes.findByRevisionId(rev1)).isEmpty()
        assertThat(routes.findByRevisionId(rev2)).hasSize(1)
    }

    @Test
    fun writeFailsLoudlyForUnregisteredType() {
        val unregistered = object : RevisionScoped(rev1) {}

        assertFailure { writer.write(listOf(unregistered)) }
            .isInstanceOf<IllegalStateException>()
            .hasMessage("No table registered for ${unregistered.javaClass.name}")
    }

    /**
     * The old writer wiped a revision through its own stateless session, so the wipe stood even when the caller's
     * transaction rolled back (`DraftService.revertToFork` relies on it). The Exposed writer keeps that independent commit.
     */
    @Test
    fun deleteAllForRevisionCommitsIndependentlyOfFailingCaller() {
        writer.write(listOf(route(rev1, "A"), route(rev1, "B")))

        assertFailure {
            TransactionTemplate(txManager).executeWithoutResult {
                writer.deleteAllForRevision(rev1)
                throw IllegalStateException("caller fails after the wipe")
            }
        }.isInstanceOf<IllegalStateException>()

        assertThat(routes.findByRevisionId(rev1)).isEmpty()
    }

    /** `write` joins the caller's transaction, as the old writer's `saveAll` did: a failing caller takes the rows with it. */
    @Test
    fun writeRollsBackWithFailingCaller() {
        assertFailure {
            TransactionTemplate(txManager).executeWithoutResult {
                writer.write(listOf(route(rev1, "A")))
                throw IllegalStateException("caller fails after the write")
            }
        }.isInstanceOf<IllegalStateException>()

        assertThat(routes.findByRevisionId(rev1)).isEmpty()
    }
}
