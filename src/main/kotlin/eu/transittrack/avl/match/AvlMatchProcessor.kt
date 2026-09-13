package eu.transittrack.avl.match

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import jakarta.annotation.PreDestroy

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.ingest.AvlWriter
import eu.transittrack.avl.ingest.VehicleStateUpsert
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.predict.PredictionService

/**
 * Claims PENDING `avl_report` rows for one feed at a time, dispatches each to the [VehicleMatcher] for
 * its feed's assignment mode, and accumulates the outcome (a `vehicle_match` row plus a `vehicle_state`
 * upsert on success; a stale `vehicle_state` upsert on failure). Outcomes for a feed are flushed as a
 * single batch once all of its claimed reports are processed, followed by a summary log line.
 *
 * Mirrors [eu.transittrack.avl.ingest.AvlPoller]'s shape: one `scheduleWithFixedDelay` task per enabled
 * feed on a dedicated [ThreadPoolTaskScheduler], reconciled every 60s against [AvlFeedRepository], so a
 * slow or high-volume feed can never delay or starve another feed's claim/processing turn. [processBatch]
 * stays as a synchronous, deterministic entry point driving every enabled feed once — used by tests and
 * manual/ops triggers; it does not touch the scheduler.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlMatchProcessor(
    private val reports: AvlReportRowRepository,
    private val feeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val contextFactory: AvlMatchContextFactory,
    matchers: List<VehicleMatcher>,
    private val writer: AvlWriter,
    private val predictionService: ObjectProvider<PredictionService>,
    private val metrics: TransitTrackMetrics,
    props: AvlProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.match
    private val matchers: Map<AvlAssignmentMode, VehicleMatcher> = matchers.associateBy { it.mode }

    private val scheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4
            setThreadNamePrefix("avl-match-")
            initialize()
        }
    private val tasks = ConcurrentHashMap<Long, ScheduledFuture<*>>()

    @EventListener(ApplicationReadyEvent::class)
    fun start() = reconcile()

    @Scheduled(fixedDelay = 60_000)
    @Synchronized
    fun reconcile() {
        val enabled = feeds.findAllEnabled().mapNotNull { it.id }.toSet()
        tasks.keys
            .filter { it !in enabled }
            .forEach { id -> tasks.remove(id)?.cancel(false) }
        for (id in enabled) {
            if (!tasks.containsKey(id)) {
                tasks[id] =
                    scheduler.scheduleWithFixedDelay(
                        { runFeed(id) },
                        Duration.ofMillis(cfg.matchIntervalMs),
                    )
            }
        }
    }

    private fun runFeed(feedId: Long) {
        runCatching { processFeed(feedId) }
            .onFailure { log.warn("avl match for feed '{}' failed", feedId, it) }
    }

    /** test hook */
    fun runningFeedIds(): Set<Long> = tasks.keys.toSet()

    @PreDestroy
    fun shutdown() = scheduler.shutdown()

    /**
     * Processes every currently-enabled feed once, synchronously, and returns the total number of
     * reports processed. Used by tests and manual/ops triggers; the scheduled path drives each feed
     * independently via [processFeed] instead.
     */
    fun processBatch(): Int =
        feeds
            .findAllEnabled()
            .sumOf { processFeed(it.id!!) }

    /** Claims and processes one feed's own PENDING batch. Returns the number of reports processed. */
    private fun processFeed(feedId: Long): Int {
        val startedAt = Instant.now()
        val feed = feeds.findById(feedId).orElse(null) ?: return 0

        fun finish(processed: Int): Int {
            metrics.avlMatchBatch(feed, TransitTrackMetrics.Outcome.SUCCESS, Duration.between(startedAt, Instant.now()), processed)
            return processed
        }

        val batch = reports.findClaimBatch(feedId, cfg.claimBatchSize)
        if (batch.isEmpty()) return finish(0)
        val ctx = contextFactory.open(feed) ?: return finish(0) // leave rows PENDING when no active revision
        val matcher = matchers[feed.assignmentMode] ?: return finish(0)

        val vehiclesForFeed = batch.groupBy { it.vehicleId }
        val stats = BatchProcessingStats(feed)
        var processed = 0

        for ((vehicleId, vehicleReports) in vehiclesForFeed) {
            // oldest-first so the sequential constraint sees a vehicle's reports in order
            val sortedReports = vehicleReports.sortedBy { it.ts }
            sortedReports.forEachIndexed { index, report ->
                val reportStartedAt = Instant.now()
                val prev = vehicleStates.findByFeedIdAndVehicleId(feedId, vehicleId)

                val match = if (index != sortedReports.lastIndex) {
                    // skip all but the newest report for each vehicle
                    MatchOutcome.Skipped
                } else {
                    matcher.match(report, prev, ctx)
                }

                stats.accumulate(report, match, prev, ctx)

                metrics.avlReportMatched(
                    feed,
                    when (match) {
                        is MatchOutcome.Matched -> TransitTrackMetrics.MatchMetricOutcome.MATCHED
                        MatchOutcome.Failed -> TransitTrackMetrics.MatchMetricOutcome.FAILED
                        MatchOutcome.Skipped -> TransitTrackMetrics.MatchMetricOutcome.SKIPPED
                    },
                    Duration.between(reportStartedAt, Instant.now()),
                    match,
                )
                processed++
            }
        }

        stats.flush()
        return finish(processed)
    }

    /** Accumulates one feed's persistence side effects so they can be flushed as a single batch. */
    private inner class BatchProcessingStats(
        val feed: AvlFeed,
    ) {
        val matches = mutableListOf<VehicleMatch>()
        val stateUpserts = mutableListOf<VehicleStateUpsert>()
        val matchedIds = mutableListOf<Long>()
        val unmatchedIds = mutableListOf<Long>()
        val skippedIds = mutableListOf<Long>()

        fun accumulate(
            report: AvlReportRow,
            outcome: MatchOutcome,
            prev: VehicleState?,
            ctx: AvlMatchContext,
        ) {
            val now = Instant.now()
            when (outcome) {
                is MatchOutcome.Matched -> {
                    matches.add(
                        VehicleMatch(
                            avlReportId = report.id!!,
                            feedId = feed.id!!,
                            vehicleId = report.vehicleId,
                            ts = report.ts,
                            revisionId = outcome.revisionId,
                            tripRowId = outcome.tripRowId,
                            blockPk = outcome.blockPk,
                            tripPatternId = outcome.tripPatternId,
                            stopPathIndex = outcome.stopPathIndex,
                            distanceAlongTripM = outcome.distanceAlongTripM,
                            deviationM = outcome.deviationM,
                            scheduleAdherenceSec = outcome.scheduleAdherenceSec,
                            snappedLat = outcome.snapped.lat,
                            snappedLon = outcome.snapped.lon,
                            heading = outcome.heading,
                            score = outcome.score,
                            createdAt = now,
                        ),
                    )
                    stateUpserts.add(
                        VehicleStateUpsert(
                            feedId = feed.id!!,
                            vehicleId = report.vehicleId,
                            vehicleLabel = report.vehicleLabel,
                            reportTs = report.ts,
                            lat = report.lat,
                            lon = report.lon,
                            bearing = report.bearing,
                            speedMps = report.speedMps,
                            occupancyStatus = report.occupancyStatus,
                            matched = true,
                            stale = false,
                            consecutiveFailures = 0,
                            revisionId = outcome.revisionId,
                            tripRowId = outcome.tripRowId,
                            blockPk = outcome.blockPk,
                            tripPatternId = outcome.tripPatternId,
                            stopPathIndex = outcome.stopPathIndex,
                            distanceAlongTripM = outcome.distanceAlongTripM,
                            scheduleAdherenceSec = outcome.scheduleAdherenceSec,
                            snappedLat = outcome.snapped.lat,
                            snappedLon = outcome.snapped.lon,
                            updatedAt = now,
                        ),
                    )
                    matchedIds.add(report.id!!)
                    predictionService.ifAvailable { it.onMatched(feed, report, prev, outcome, ctx) }
                }

                MatchOutcome.Failed -> {
                    val failures = (prev?.consecutiveFailures ?: 0) + 1
                    val cleared = failures >= cfg.unmatchAfterFailures
                    stateUpserts.add(
                        VehicleStateUpsert(
                            feedId = feed.id!!,
                            vehicleId = report.vehicleId,
                            vehicleLabel = report.vehicleLabel,
                            reportTs = report.ts,
                            lat = report.lat,
                            lon = report.lon,
                            bearing = report.bearing,
                            speedMps = report.speedMps,
                            occupancyStatus = report.occupancyStatus,
                            matched = false,
                            stale = true,
                            consecutiveFailures = failures,
                            revisionId = if (cleared) null else prev?.revisionId,
                            tripRowId = if (cleared) null else prev?.tripRowId,
                            blockPk = if (cleared) null else prev?.blockPk,
                            tripPatternId = if (cleared) null else prev?.tripPatternId,
                            stopPathIndex = if (cleared) null else prev?.stopPathIndex,
                            distanceAlongTripM = if (cleared) null else prev?.distanceAlongTripM,
                            scheduleAdherenceSec = if (cleared) null else prev?.scheduleAdherenceSec,
                            snappedLat = if (cleared) null else prev?.snappedLat,
                            snappedLon = if (cleared) null else prev?.snappedLon,
                            updatedAt = now,
                        ),
                    )

                    unmatchedIds.add(report.id!!)
                }

                MatchOutcome.Skipped -> {
                    skippedIds.add(report.id!!)
                }
            }
        }

        fun flush() {
            writer.insertMatches(matches)
            writer.upsertStates(stateUpserts)
            val at = Instant.now()

            if (matchedIds.isNotEmpty()) {
                reports.markMatchedBatch(matchedIds, MatchStatus.MATCHED, at)
            }

            if (unmatchedIds.isNotEmpty()) {
                reports.markMatchedBatch(unmatchedIds, MatchStatus.UNMATCHED, at)
            }

            if (skippedIds.isNotEmpty()) {
                reports.markMatchedBatch(skippedIds, MatchStatus.SKIPPED, at)
            }

            log.info(
                "avl match feed={} ✅ matched={} ⚠️ skipped={} ‼️ rejected={}",
                feed.code,
                matchedIds.size,
                skippedIds.size,
                unmatchedIds.size,
            )
        }
    }
}
