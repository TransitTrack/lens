package eu.transittrack.schedule.optimize

import java.time.Instant

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

data class OptimizationRunRequest(
    val feedCode: String,
    val observedFrom: Instant,
    val observedTo: Instant,
    val minimumSamples: Int,
)

@Service
class ScheduleOptimizationService(
    private val feeds: GtfsFeedRepository,
    private val revisions: GtfsRevisionRepository,
    private val runs: OptimizationRunRepository,
) {
    fun submit(request: OptimizationRunRequest): OptimizationRunRow {
        val feed = feeds.findByCode(request.feedCode) ?: throw IllegalArgumentException("no feed '${request.feedCode}'")
        val revision = revisions.findByFeedAndStatus(feed.id!!, GtfsRevisionStatus.ACTIVE)
            ?: throw IllegalArgumentException("feed '${request.feedCode}' has no ACTIVE revision")
        val run = OptimizationRun.newRun(feed.id!!, revision.id!!, request.observedFrom, request.observedTo, request.minimumSamples)
        return runs.save(
            OptimizationRunRow(run.feedId, run.revisionId, run.observedFrom, run.observedTo, run.minimumSamples, run.state),
        )
    }
}
