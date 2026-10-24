package eu.transittrack.avl.match

import java.time.Duration
import java.time.Instant

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
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
import eu.transittrack.bearingDegrees
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.haversineMeters
import eu.transittrack.util.concurrency.FeedLockCoordinator
import eu.transittrack.util.concurrency.FeedReconciler
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Claims PENDING `avl_report` rows for one feed at a time, dispatches each to the [VehicleMatcher] for
 * its feed's assignment mode, and accumulates the outcome (a `vehicle_match` row plus a `vehicle_state`
 * upsert on success; a stale `vehicle_state` upsert on failure). Outcomes for a feed are flushed as a
 * single batch once all of its claimed reports are processed, followed by a summary log line.
 *
 * The task lifecycle (one `scheduleWithFixedDelay` task per enabled feed, reconciled every 30s, so a
 * slow or high-volume feed can never delay or starve another feed's claim/processing turn) lives in
 * [FeedReconciler]. [processBatch] stays as a synchronous, deterministic entry point driving every
 * enabled feed once — used by tests and manual/ops triggers; it does not touch the scheduler.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class AvlMatchProcessor(
    private val reports: AvlReportRowRepository,
    feeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val contextFactory: AvlMatchContextFactory,
    matchers: List<VehicleMatcher>,
    private val writer: AvlWriter,
    private val metrics: TransitTrackMetrics,
    props: AvlProperties,
    @Qualifier("avlFeedLockCoordinator") feedLocks: FeedLockCoordinator,
    @Value("\${spring.threads.virtual.enabled:false}") useVirtualThreads: Boolean,
) : FeedReconciler(feeds, feedLocks, "avl-match-", useVirtualThreads) {
    private val cfg = props.match
    private val matchers: Map<AvlAssignmentMode, VehicleMatcher> = matchers.associateBy { it.mode }

    override fun intervalFor(feed: AvlFeed): Duration = Duration.ofMillis(cfg.matchIntervalMs)

    override fun isEligible(feed: AvlFeed): Boolean = contextFactory.hasActiveRevision(feed)

    override fun run(feedCode: String) {
        runCatching { processFeed(feedCode) }
            .onFailure { log.warn("avl match for feed '{}' failed", feedCode, it) }
    }

    @Scheduled(fixedDelay = 60_000)
    // See AvlPoller.reconcile for why lockAtLeastFor is ~zero rather than a nonzero minimum.
    @SchedulerLock(name = "avl-match-processor-reconcile-lock", lockAtMostFor = "PT30M", lockAtLeastFor = "PT0S")
    override fun reconcile() = reconcileTasks()

    /**
     * Processes every currently-enabled feed once, synchronously, and returns the total number of
     * reports processed. Used by tests and manual/ops triggers; the scheduled path drives each feed
     * independently via [processFeed] instead.
     */
    fun processBatch(): Int =
        feeds
            .findAllEnabled()
            .sumOf { processFeed(it.code) }

    /** Claims and processes one feed's own PENDING batch. Returns the number of reports processed. */
    private fun processFeed(feedCode: String): Int {
        val startedAt = Instant.now()
        val feed = feeds.findByCode(feedCode) ?: return 0

        log.info("processing matches for feed '{}'", feed.code)

        fun finish(
            processed: Int,
            reason: String? = null,
        ): Int {
            log.info("finished processing matches for feed '{}' result {}, reason: {}", feed.code, processed, reason)
            metrics.avlMatchBatch(feed.code, TransitTrackMetrics.Outcome.SUCCESS, Duration.between(startedAt, Instant.now()), processed)
            return processed
        }

        val batch = reports.findClaimBatch(feed.code, cfg.claimBatchSize)
        if (batch.isEmpty()) return finish(0, "no batch found")
        val ctx = contextFactory.open(feed) ?: return finish(0, "failed to open context") // leave rows PENDING when no active revision
        val matcher = matchers[feed.assignmentMode] ?: return finish(0, "no matchers found for assignment mode")

        val vehiclesForFeed = batch.groupBy { it.vehicleId }
        val vehiclesPreviousStates = vehicleStates.findByFeedCode(feed.code).associateBy { it.vehicleId }
        val stats = BatchProcessingStats(feed)
        var processed = 0

        for ((vehicleId, vehicleReports) in vehiclesForFeed) {
            val prev = vehiclesPreviousStates[vehicleId]
            var cursorLat = prev?.lat
            var cursorLon = prev?.lon
            var cursorTs = prev?.reportTs

            // oldest-first so the sequential constraint sees a vehicle's reports in order
            vehicleReports
                .sortedBy { it.ts }
                .forEach { report ->
                    // Many GTFS-RT feeds omit bearing/speed; derive them from the vehicle's last
                    // known position when the feed itself doesn't report them. The cursor walks the
                    // batch's own reports (not just `prev`) so a multi-report batch derives each
                    // report's kinematics from its immediate predecessor, not a stale starting point.
                    report.fillMissingKinematics(cursorLat, cursorLon, cursorTs)
                    cursorLat = report.lat
                    cursorLon = report.lon
                    cursorTs = report.ts

                    val match = matcher.match(report.toView(), prev?.toView(), ctx)

                    stats.accumulate(report, match, prev, ctx)

                    metrics.avlReportMatched(
                        feed.code,
                        feed.assignmentMode.name.lowercase(),
                        when (match) {
                            is MatchOutcome.Matched -> TransitTrackMetrics.MatchMetricOutcome.MATCHED
                            MatchOutcome.Failed -> TransitTrackMetrics.MatchMetricOutcome.FAILED
                            MatchOutcome.Skipped -> TransitTrackMetrics.MatchMetricOutcome.SKIPPED
                        },
                        Duration.between(report.createdAt, Instant.now()),
                        deviationM = (match as? MatchOutcome.Matched)?.deviationM,
                        score = (match as? MatchOutcome.Matched)?.score,
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
                            feedCode = feed.code,
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
                            feedCode = feed.code,
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
                }

                MatchOutcome.Failed -> {
                    val failures = (prev?.consecutiveFailures ?: 0) + 1
                    val cleared = failures >= cfg.unmatchAfterFailures
                    stateUpserts.add(
                        VehicleStateUpsert(
                            feedCode = feed.code,
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

/**
 * Fills [AvlReportRow.bearing]/[AvlReportRow.speedMps] from the vehicle's last known position when
 * the feed itself didn't report them, instead of leaving the matcher/vehicle-state without a heading
 * or speed signal. `prevTs`/`ts` equal or out of order (e.g. a duplicate/backdated report) is left
 * alone rather than dividing by a non-positive elapsed time.
 */
private fun AvlReportRow.fillMissingKinematics(
    prevLat: Double?,
    prevLon: Double?,
    prevTs: Instant?,
) {
    if (prevLat == null || prevLon == null || prevTs == null) return
    val elapsedSec = Duration.between(prevTs, ts).seconds
    if (elapsedSec <= 0) return
    if (bearing == null) bearing = bearingDegrees(prevLat, prevLon, lat, lon)
    if (speedMps == null) speedMps = haversineMeters(prevLat, prevLon, lat, lon) / elapsedSec
}

private fun AvlReportRow.toView() =
    AvlReportView(
        id = id!!,
        feedCode = feedCode,
        vehicleId = vehicleId,
        vehicleLabel = vehicleLabel,
        ts = ts,
        lat = lat,
        lon = lon,
        bearing = bearing,
        speedMps = speedMps,
        odometerM = odometerM,
        descTripId = descTripId,
        descRouteId = descRouteId,
        descDirectionId = descDirectionId,
        descStartDate = descStartDate,
        descStartTimeSec = descStartTimeSec,
        descScheduleRelationship = descScheduleRelationship,
        currentStopSequence = currentStopSequence,
        currentStopId = currentStopId,
        currentStatus = currentStatus,
        occupancyStatus = occupancyStatus,
        congestionLevel = congestionLevel,
    )

private fun VehicleState.toView() =
    VehicleStateView(
        id = id,
        feedCode = feedCode,
        vehicleId = vehicleId,
        vehicleLabel = vehicleLabel,
        reportTs = reportTs,
        lat = lat,
        lon = lon,
        bearing = bearing,
        speedMps = speedMps,
        occupancyStatus = occupancyStatus,
        matched = matched,
        stale = stale,
        consecutiveFailures = consecutiveFailures,
        revisionId = revisionId,
        tripRowId = tripRowId,
        blockPk = blockPk,
        tripPatternId = tripPatternId,
        stopPathIndex = stopPathIndex,
        distanceAlongTripM = distanceAlongTripM,
        scheduleAdherenceSec = scheduleAdherenceSec,
        snappedLat = snappedLat,
        snappedLon = snappedLon,
    )
