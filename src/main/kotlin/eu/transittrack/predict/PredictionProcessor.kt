package eu.transittrack.predict

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import jakarta.annotation.PreDestroy

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.PredictionStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.concurrency.FeedLockCoordinator
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.observability.TransitTrackMetrics

/**
 * Claims PENDING `vehicle_match` rows for one feed at a time and dispatches each to
 * [PredictionService.onMatched], entirely independent of [eu.transittrack.avl.match.AvlMatchProcessor]'s
 * own cycle — every successful match already becomes one `vehicle_match` row, so that table is this
 * processor's durable backlog. A feed with a large backlog (e.g. many hundreds of active vehicles)
 * simply takes longer to drain, or drains across several of its own cycles; nothing is ever dropped,
 * unlike the fixed-capacity executor this replaces.
 *
 * Mirrors [eu.transittrack.avl.match.AvlMatchProcessor]'s shape exactly: one `scheduleWithFixedDelay`
 * task per enabled feed on a dedicated [ThreadPoolTaskScheduler], reconciled every 60s. [processBatch]
 * is the synchronous, deterministic entry point used by tests and manual/ops triggers.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.PREDICTOR)
class PredictionProcessor(
    private val vehicleMatches: VehicleMatchRepository,
    private val reports: AvlReportRowRepository,
    private val feeds: AvlFeedRepository,
    private val contextFactory: AvlMatchContextFactory,
    private val predictionService: PredictionService,
    private val metrics: TransitTrackMetrics,
    props: PredictProperties,
    @Qualifier("predictorFeedLockCoordinator") private val feedLocks: FeedLockCoordinator,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.run

    private val scheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4
            setThreadNamePrefix("avl-predict-")
            initialize()
        }
    private val tasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    @EventListener(ApplicationReadyEvent::class)
    fun start() = reconcile()

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "prediction-processor-reconcile-lock", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10M")
    fun reconcile() {
        val enabled = feeds.findAllEnabled().mapNotNull { it.id }.toSet()
        val owned = feedLocks.reconcileOwnership(enabled)
        tasks.keys
            .filter { it !in owned }
            .forEach { id -> tasks.remove(id)?.cancel(false) }
        for (id in owned) {
            if (!tasks.containsKey(id)) {
                tasks[id] =
                    scheduler.scheduleWithFixedDelay(
                        { runFeed(id) },
                        Duration.ofMillis(cfg.intervalMs),
                    )
            }
        }
    }

    private fun runFeed(feedId: Long) {
        runCatching { processFeed(feedId) }
            .onFailure { log.warn("prediction processing for feed '{}' failed", feedId, it) }
    }

    /** test hook */
    fun runningFeedIds(): Set<Long> = tasks.keys.toSet()

    @PreDestroy
    fun shutdown() {
        feedLocks.releaseAll()
        scheduler.shutdown()
    }

    /**
     * Processes every currently-enabled feed once, synchronously, and returns the total number of
     * `vehicle_match` rows processed. Used by tests and manual/ops triggers; the scheduled path drives
     * each feed independently via [processFeed] instead.
     */
    fun processBatch(): Int =
        feeds
            .findAllEnabled()
            .sumOf { processFeed(it.id!!) }

    /** Claims and processes one feed's own PENDING `vehicle_match` batch. Returns rows processed. */
    private fun processFeed(feedId: Long): Int {
        val startedAt = Instant.now()
        val feed = feeds.findById(feedId).orElse(null) ?: return 0

        fun finish(
            processed: Int,
            failed: Int,
        ): Int {
            val outcome = if (failed > 0) TransitTrackMetrics.Outcome.FAILED else TransitTrackMetrics.Outcome.SUCCESS
            metrics.predictionBatch(feed, outcome, Duration.between(startedAt, Instant.now()), processed)
            return processed
        }

        val batch = vehicleMatches.findClaimBatch(feedId, cfg.claimBatchSize)
        if (batch.isEmpty()) return finish(0, 0)

        val doneIds = mutableListOf<Long>()
        val failedIds = mutableListOf<Long>()
        val contextsByRevision = HashMap<Long, eu.transittrack.avl.match.AvlMatchContext>()

        for ((vehicleId, vehicleMatchesForVehicle) in batch.groupBy { it.vehicleId }) {
            val sorted = vehicleMatchesForVehicle.sortedBy { it.ts }
            var prev = vehicleMatches.findTopByFeedIdAndVehicleIdAndTsLessThanOrderByTsDesc(feedId, vehicleId, sorted.first().ts)?.toPrev()

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
