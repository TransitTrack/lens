package eu.transittrack.predict

import java.time.Duration
import java.time.Instant

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContext
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.PredictionStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.util.concurrency.FeedLockCoordinator
import eu.transittrack.util.concurrency.FeedReconciler
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Claims PENDING `vehicle_match` rows for one feed at a time and dispatches each to
 * [PredictionService.onMatched], entirely independent of [eu.transittrack.avl.match.AvlMatchProcessor]'s
 * own cycle — every successful match already becomes one `vehicle_match` row, so that table is this
 * processor's durable backlog. A feed with a large backlog (e.g. many hundreds of active vehicles)
 * simply takes longer to drain, or drains across several of its own cycles; nothing is ever dropped,
 * unlike the fixed-capacity executor this replaces.
 *
 * The task lifecycle (one `scheduleWithFixedDelay` task per enabled feed, reconciled every 30s) lives
 * in [FeedReconciler]. [processBatch] is the synchronous, deterministic entry point used by tests and
 * manual/ops triggers.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.PREDICTOR)
class PredictionProcessor(
    private val vehicleMatches: VehicleMatchRepository,
    private val reports: AvlReportRowRepository,
    feeds: AvlFeedRepository,
    private val contextFactory: AvlMatchContextFactory,
    private val predictionService: PredictionService,
    private val metrics: TransitTrackMetrics,
    props: PredictProperties,
    @Qualifier("predictorFeedLockCoordinator") feedLocks: FeedLockCoordinator,
    @Value("\${spring.threads.virtual.enabled:false}") useVirtualThreads: Boolean,
) : FeedReconciler(feeds, feedLocks, "avl-predict-", useVirtualThreads) {
    private val cfg = props.run

    override fun intervalFor(feed: AvlFeed): Duration = Duration.ofMillis(cfg.intervalMs)

    override fun isEligible(feed: AvlFeed): Boolean = contextFactory.hasActiveRevision(feed)

    override fun run(feedCode: String) {
        runCatching { processFeed(feedCode) }
            .onFailure { log.warn("prediction processing for feed '{}' failed", feedCode, it) }
    }

    @Scheduled(fixedDelay = 60_000)
    // See AvlPoller.reconcile for why lockAtLeastFor is ~zero rather than a nonzero minimum.
    @SchedulerLock(name = "prediction-processor-reconcile-lock", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    override fun reconcile() = reconcileTasks()

    /**
     * Processes every currently-enabled feed once, synchronously, and returns the total number of
     * `vehicle_match` rows processed. Used by tests and manual/ops triggers; the scheduled path drives
     * each feed independently via [processFeed] instead.
     */
    fun processBatch(): Int =
        feeds
            .findAllEnabled()
            .sumOf { processFeed(it.code) }

    /** Claims and processes one feed's own PENDING `vehicle_match` batch. Returns rows processed. */
    private fun processFeed(feedId: String): Int {
        val startedAt = Instant.now()
        val feed = feeds.findByCode(feedId) ?: return 0

        fun finish(
            processed: Int,
            failed: Int,
        ): Int {
            val outcome = if (failed > 0) TransitTrackMetrics.Outcome.FAILED else TransitTrackMetrics.Outcome.SUCCESS
            metrics.predictionBatch(feed.code, outcome, Duration.between(startedAt, Instant.now()), processed)
            return processed
        }

        val batch = vehicleMatches.findClaimBatch(feed.code, cfg.claimBatchSize)
        if (batch.isEmpty()) return finish(0, 0)

        val doneIds = mutableListOf<Long>()
        val failedIds = mutableListOf<Long>()
        val contextsByRevision = HashMap<Long, AvlMatchContext>()

        for ((vehicleId, vehicleMatchesForVehicle) in batch.groupBy { it.vehicleId }) {
            val sorted = vehicleMatchesForVehicle.sortedBy { it.ts }
            var prev =
                vehicleMatches
                    .findPreviousForVehicle(feed.code, vehicleId, sorted.first().ts)
                    ?.toPrev()

            for (row in sorted) {
                val outcome = row.toMatchOutcome()
                if (outcome == null) {
                    log.warn("vehicle_match {} has no trip_pattern_id, cannot generate predictions", row.id)
                    failedIds.add(row.id!!)
                    continue
                }
                val report = reports.findById(row.avlReportId).orElse(null)
                if (report == null) {
                    failedIds.add(row.id!!)
                    continue
                }
                val ctx = contextsByRevision.getOrPut(row.revisionId) { contextFactory.openForRevision(row.revisionId) }

                runCatching { predictionService.onMatched(feed, report, prev, outcome, ctx) }
                    .onFailure {
                        log.warn("prediction generation for feed '{}' vehicle '{}' failed", feed.code, vehicleId, it)
                        failedIds.add(row.id!!)
                    }.onSuccess { doneIds.add(row.id!!) }

                prev = row.toPrev()
            }
        }

        val at = Instant.now()
        if (doneIds.isNotEmpty()) vehicleMatches.markPredicted(doneIds, PredictionStatus.DONE, at)
        if (failedIds.isNotEmpty()) vehicleMatches.markPredicted(failedIds, PredictionStatus.FAILED, at)

        return finish(doneIds.size + failedIds.size, failedIds.size)
    }
}

private fun VehicleMatch.toPrev() = PrevVehicleMatch(tripRowId, stopPathIndex, ts)

private fun VehicleMatch.toMatchOutcome(): MatchOutcome.Matched? {
    val patternId = tripPatternId ?: return null
    return MatchOutcome.Matched(
        tripRowId = tripRowId,
        blockPk = blockPk,
        tripPatternId = patternId,
        stopPathIndex = stopPathIndex,
        distanceAlongTripM = distanceAlongTripM,
        deviationM = deviationM,
        scheduleAdherenceSec = scheduleAdherenceSec,
        snapped = eu.transittrack.Point(snappedLat, snappedLon),
        heading = heading,
        score = score,
        revisionId = revisionId,
    )
}
