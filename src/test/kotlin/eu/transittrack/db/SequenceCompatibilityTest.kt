package eu.transittrack.db

import java.time.Instant
import jakarta.persistence.EntityManager

import assertk.assertThat
import assertk.assertions.hasSize
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.support.PostgresPerMethodTest

/**
 * Interim: Hibernate's pooled optimizer hands out a block of ids per `nextval` while Exposed takes
 * one `nextval` per insert; both must keep drawing distinct ids from the same sequence. Deleted with
 * the JPA entities.
 */
@SpringBootTest
class SequenceCompatibilityTest(
    @Autowired val em: EntityManager,
    @Autowired val txManager: PlatformTransactionManager,
    @Autowired val exposedDatabase: Database,
) : PostgresPerMethodTest() {
    private var n = 0

    private fun feed(): GtfsFeed {
        n++
        return GtfsFeed(
            code = "seq$n",
            name = "Seq",
            description = null,
            url = "http://x/f.zip",
            pollingCron = null,
            enabled = true,
            autoActivate = null,
            source = FeedSource.API,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }

    private fun persistThree(): List<Long> =
        TransactionTemplate(txManager).execute {
            val feeds = List(3) { feed().also(em::persist) }
            em.flush()
            feeds.map { it.id!! }
        }!!

    @Test
    fun pooledAndNextvalIdsNeverCollide() {
        val first = persistThree()
        val exposed =
            transaction(exposedDatabase) {
                List(3) {
                    val f = feed()
                    GtfsFeedTable.insert { GtfsFeedTable.bind(it, f) }[GtfsFeedTable.id]
                }
            }
        val last = persistThree()

        assertThat((first + exposed + last).distinct()).hasSize(9)
    }
}
