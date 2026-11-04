package eu.transittrack.gtfs.revision

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class RevisionLockTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val edits: DraftEditRepository,
    @Autowired val txManager: PlatformTransactionManager,
) : PostgresPerMethodTest() {
    private fun newRevision(): Long {
        val feed =
            feeds.save(
                GtfsFeed(
                    code = "lock",
                    name = "Lock",
                    description = null,
                    url = "http://x/f.zip",
                    pollingCron = null,
                    enabled = true,
                    autoActivate = null,
                    source = FeedSource.API,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        return revisions.save(GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.DRAFT, sourceUrl = "http://x/f.zip")).id!!
    }

    @Test
    fun lockForUpdateBlocksSecondTransaction() {
        val id = newRevision()
        val pool = Executors.newFixedThreadPool(2)
        val aHoldsLock = CountDownLatch(1)
        try {
            val a =
                pool.submit {
                    TransactionTemplate(txManager).executeWithoutResult {
                        revisions.findByIdForUpdate(id)
                        aHoldsLock.countDown()
                        Thread.sleep(500)
                    }
                }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "thread A never took the lock" }
            val b =
                pool.submit<Long> {
                    measureTimeMillis {
                        TransactionTemplate(txManager).executeWithoutResult { revisions.findByIdForUpdate(id) }
                    }
                }
            a.get(10, TimeUnit.SECONDS)
            assertThat(b.get(10, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(450L)
        } finally {
            pool.shutdownNow()
        }
    }

    /** `for no key update` must not conflict with the `for key share` an FK-referencing insert takes. */
    @Test
    fun lockDoesNotBlockInsertOfReferencingRow() {
        val id = newRevision()
        val pool = Executors.newFixedThreadPool(2)
        val aHoldsLock = CountDownLatch(1)
        try {
            val a =
                pool.submit {
                    TransactionTemplate(txManager).executeWithoutResult {
                        revisions.findByIdForUpdate(id)
                        aHoldsLock.countDown()
                        Thread.sleep(1500)
                    }
                }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "thread A never took the lock" }
            val b =
                pool.submit<Long> {
                    measureTimeMillis {
                        edits.save(DraftEdit(id, 1, "op", "s", "{}", "{}", Instant.now()))
                    }
                }
            assertThat(b.get(10, TimeUnit.SECONDS)).isLessThan(1000L)
            a.get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }
}
