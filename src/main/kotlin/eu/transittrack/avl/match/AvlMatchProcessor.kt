package eu.transittrack.avl.match

import java.time.Duration
import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
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
 * Claims PENDING `avl_report` rows, dispatches each to the [VehicleMatcher] for its feed's assignment
 * mode, and accumulates the outcome (a `vehicle_match` row plus a `vehicle_state` upsert on success; a
 * stale `vehicle_state` upsert on failure). Outcomes for a feed are flushed as a single batch once all
 * of its reports are processed, followed by a summary log line. Runs on a fixed delay; [processBatch]
 * is public so tests can drive it directly.
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

    @Scheduled(fixedDelayString = "\${transittrack.avl.match.match-interval-ms:5000}")
    fun run() {
        runCatching { processBatch() }.onFailure { log.warn("avl match batch failed", it) }
    }

    /** Returns the number of reports processed. Public so tests can drive it directly. */
    fun processBatch(): Int {
        val batchStartedAt = Instant.now()
        val batch = reports.findClaimBatch(cfg.claimBatchSize)
        if (batch.isEmpty()) {
            metrics.avlMatchBatch(TransitTrackMetrics.Outcome.SUCCESS, Duration.between(batchStartedAt, Instant.now()), 0)
            return 0
        }

        val byFeed = batch.groupBy { it.feedId }
        var processed = 0
        for ((feedId, feedReports) in byFeed) {
            val feed = feeds.findById(feedId).orElse(null) ?: continue
            val ctx = contextFactory.open(feed) ?: continue // leave rows PENDING when no active revision
            val matcher = matchers[feed.assignmentMode] ?: continue
            val vehiclesForFeed = feedReports.groupBy { it.vehicleId }
            val vehiclesPreviousStates = vehicleStates
                .findByFeedId(feedId)
                .associateBy { it.vehicleId }

            val stats = BatchProcessingStats(feed)

            for ((vehicleId, vehicleReports) in vehiclesForFeed) {
                val prev = vehiclesPreviousStates[vehicleId]

                // oldest-first so the sequential constraint sees a vehicle's reports in order
                vehicleReports
                    .sortedBy { it.ts }
                    .forEach { report ->
                        val reportStartedAt = Instant.now()
                        val match = matcher.match(report, prev, ctx)

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
        }
        metrics.avlMatchBatch(TransitTrackMetrics.Outcome.SUCCESS, Duration.between(batchStartedAt, Instant.now()), processed)
        return processed
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
