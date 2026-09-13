package eu.transittrack.schedule.optimize

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.task.TaskExecutor

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.schedule.optimize.model.OptimizationRunState

/** Runs each submitted task only when [runAll] is called, so tests control exactly when a queued job executes. */
private class CapturingExecutor : TaskExecutor {
    private val tasks = mutableListOf<Runnable>()

    override fun execute(task: Runnable) {
        tasks += task
    }

    fun runAll() {
        val pending = tasks.toList()
        tasks.clear()
        pending.forEach { it.run() }
    }
}

/** In-memory fake standing in for a JPA repository so `save`/`findById` observe the same row instance. */
private class FakeRunRepository : OptimizationRunRepository by mock() {
    private val rows = LinkedHashMap<Long, OptimizationRunRow>()
    private var nextId = 1L

    override fun <S : OptimizationRunRow> save(entity: S): S {
        if (entity.id == null) entity.id = nextId++
        rows[entity.id!!] = entity
        return entity
    }

    override fun findById(id: Long) = java.util.Optional.ofNullable(rows[id])
}

class ScheduleOptimizationServiceTest {
    private fun validRequest(feedCode: String = "g") =
        OptimizationRunRequest(
            feedCode = feedCode,
            observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
            observedTo = Instant.parse("2026-09-08T00:00:00Z"),
            minimumSamples = 20,
        )

    private fun feed(id: Long = 7) = GtfsFeed("g", "G", null, "x", null, true, null, FeedSource.API, Instant.EPOCH, Instant.EPOCH, id = id)

    private fun revision(
        feedId: Long = 7,
        id: Long = 11,
    ) = GtfsRevision(feedId, GtfsRevisionStatus.ACTIVE, "x", id = id)

    @Test
    fun `submits a queued run against the feed active revision`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = FakeRunRepository()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(feeds.findByCode("g")).thenReturn(feed())
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision())

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, CapturingExecutor())
        val result = service.submit(validRequest())

        assertThat(result.revisionId).isEqualTo(11)
        assertThat(result.state).isEqualTo(OptimizationRunState.QUEUED)
    }

    @Test
    fun `rejects a feed with no active revision before dispatching anything`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = FakeRunRepository()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(feeds.findByCode("g")).thenReturn(feed())
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(null)

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, CapturingExecutor())

        assertThrows<IllegalArgumentException> { service.submit(validRequest()) }
    }

    @Test
    fun `rejects an invalid request before any repository access`() {
        val feeds = mock<GtfsFeedRepository>()
        assertThrows<IllegalArgumentException> {
            OptimizationRunRequest(
                feedCode = "g",
                observedFrom = Instant.parse("2026-09-08T00:00:00Z"),
                observedTo = Instant.parse("2026-09-01T00:00:00Z"),
                minimumSamples = 20,
            )
        }
        assertThrows<IllegalArgumentException> {
            OptimizationRunRequest(
                feedCode = "g",
                observedFrom = Instant.EPOCH,
                observedTo = Instant.EPOCH.plusSeconds(1),
                minimumSamples = 0,
            )
        }
        assertThrows<IllegalArgumentException> {
            OptimizationRunRequest(
                feedCode = "g",
                observedFrom = Instant.EPOCH,
                observedTo = Instant.EPOCH.plusSeconds(1),
                minimumSamples = 1,
                windowFromSec = 3600,
                windowToSec = 1800,
            )
        }
        assertThrows<IllegalArgumentException> {
            OptimizationRunRequest(
                feedCode = "g",
                observedFrom = Instant.EPOCH,
                observedTo = Instant.EPOCH.plusSeconds(1),
                minimumSamples = 1,
                windowFromSec = 3600,
                windowToSec = null,
            )
        }
        // None of the above should have touched a repository.
        org.mockito.kotlin.verifyNoInteractions(feeds)
    }

    @Test
    fun `submission freezes the then-active revision even if a later revision is activated before the queued job runs`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = FakeRunRepository()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(feeds.findByCode("g")).thenReturn(feed())
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision(id = 11))
        val executor = CapturingExecutor()

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, executor)
        val run = service.submit(validRequest())

        // A newer revision is activated after submission but before the queued job runs.
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision(id = 99))
        executor.runAll()

        assertThat(service.get(run.id!!)!!.revisionId).isEqualTo(11)
    }

    @Test
    fun `an empty eligible population completes as SUCCEEDED with no recommendations`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = FakeRunRepository()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(feeds.findByCode("g")).thenReturn(feed())
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision())
        val executor = CapturingExecutor()

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, executor)
        val run = service.submit(validRequest())
        executor.runAll()

        val loaded = service.get(run.id!!)!!
        assertThat(loaded.state).isEqualTo(OptimizationRunState.SUCCEEDED)
        assertThat(loaded.startedAt).isNotNull()
        assertThat(loaded.completedAt).isNotNull()
        assertThat(loaded.error).isNull()
        org.mockito.kotlin.verifyNoInteractions(recommendations)
    }

    @Test
    fun `an unexpected failure during execution is marked FAILED with a sanitized error`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(feeds.findByCode("g")).thenReturn(feed())
        whenever(revisions.findByFeedAndStatus(7, GtfsRevisionStatus.ACTIVE)).thenReturn(revision())

        // A repository backed by a real map, but the *first* save (the RUNNING transition) throws to
        // simulate an unexpected mid-run failure; subsequent saves (the terminal FAILED write) succeed.
        var failuresLeft = 1
        val rows = LinkedHashMap<Long, OptimizationRunRow>()
        var nextId = 1L
        val runs =
            object : OptimizationRunRepository by mock() {
                override fun <S : OptimizationRunRow> save(entity: S): S {
                    if (entity.id == null) entity.id = nextId++
                    if (entity.state == OptimizationRunState.RUNNING && failuresLeft > 0) {
                        failuresLeft--
                        throw RuntimeException("leaking sensitive detail: password=hunter2")
                    }
                    rows[entity.id!!] = entity
                    return entity
                }

                override fun findById(id: Long) = java.util.Optional.ofNullable(rows[id])
            }
        val executor = CapturingExecutor()

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, executor)
        val run = service.submit(validRequest())
        executor.runAll()

        val loaded = service.get(run.id!!)!!
        assertThat(loaded.state).isEqualTo(OptimizationRunState.FAILED)
        assertThat(loaded.completedAt).isNotNull()
        assertThat(loaded.error).isEqualTo("optimization run failed unexpectedly; see server logs")
    }

    @Test
    fun `listRecommendations delegates to the stable run id, status, id paged query`() {
        val feeds = mock<GtfsFeedRepository>()
        val revisions = mock<GtfsRevisionRepository>()
        val runs = mock<OptimizationRunRepository>()
        val recommendations = mock<OptimizationRecommendationRepository>()
        val pipeline = mock<OptimizationAnalysisPipeline>()
        whenever(recommendations.page(any(), any(), any(), any())).thenReturn(emptyList())

        val service = ScheduleOptimizationService(feeds, revisions, runs, recommendations, pipeline, CapturingExecutor())
        service.listRecommendations(42, eu.transittrack.schedule.optimize.model.OptimizationRecommendationStatus.PENDING, 10, 25)

        org.mockito.kotlin
            .verify(recommendations)
            .page(42, "PENDING", 10, 25)
    }
}
