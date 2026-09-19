package eu.transittrack.gtfs.ingest

import java.time.Instant
import kotlin.test.Test

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

import eu.transittrack.GtfsProperties
import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.support.FakeLockProvider

class GtfsIngestSchedulerTest {
    private val ingestion = mock<IngestionService>()
    private val lockingExecutor = DefaultLockingTaskExecutor(FakeLockProvider())

    private fun feed(
        code: String,
        cron: String?,
        last: Instant?,
    ) = GtfsFeed(
        code,
        code,
        null,
        "u",
        cron,
        true,
        null,
        FeedSource.CONFIG,
        Instant.now(),
        Instant.now(),
        last,
    )

    @Test
    fun `triggers only feeds that are due`() {
        val repo = mock<GtfsFeedRepository>()
        whenever(repo.findAllEnabled())
            .thenReturn(
                listOf(
                    feed("due", "0 0 3 * * *", Instant.parse("2000-01-01T00:00:00Z")),
                    feed("not-due", "0 0 3 * * *", Instant.now()),
                    feed("manual", null, null),
                ),
            )
        val props = GtfsProperties(polling = GtfsProperties.Polling(enabled = true))

        GtfsIngestScheduler(repo, ingestion, props, lockingExecutor, TransitTrackMetrics.forTests()).sweep()

        verify(ingestion, times(1)).ingest(eq("due"))
        verify(ingestion, never()).ingest(eq("not-due"))
        verify(ingestion, never()).ingest(eq("manual"))
    }

    @Test
    fun `no-op when polling disabled`() {
        val repo = mock<GtfsFeedRepository>()

        GtfsIngestScheduler(repo, ingestion, GtfsProperties(), lockingExecutor, TransitTrackMetrics.forTests()).sweep()

        verify(ingestion, never()).ingest(any())
    }

    @Test
    fun `sweep does not ingest a due feed whose lock another actor already holds`() {
        val repo = mock<GtfsFeedRepository>()
        whenever(repo.findAllEnabled())
            .thenReturn(listOf(feed("f1", "0 0 3 * * *", Instant.parse("2000-01-01T00:00:00Z"))))
        val props = GtfsProperties(polling = GtfsProperties.Polling(enabled = true))
        val sharedLockProvider = FakeLockProvider()
        val scheduler =
            GtfsIngestScheduler(repo, ingestion, props, DefaultLockingTaskExecutor(sharedLockProvider), TransitTrackMetrics.forTests())

        val held =
            sharedLockProvider.lock(
                net.javacrumbs.shedlock.core.LockConfiguration(
                    Instant.now(), "gtfs-ingest:f1",
                    java.time.Duration.ofMinutes(30), java.time.Duration.ofSeconds(1),
                ),
            )
        assert(held.isPresent)

        scheduler.sweep()

        verify(ingestion, never()).ingest(eq("f1"))
    }
}
