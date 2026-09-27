package eu.transittrack.avl.observability

import java.time.Instant

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.observability.TransitTrackMetrics
import eu.transittrack.predict.PredictProperties

/**
 * Refreshes database-derived queue gauges outside Prometheus's scrape thread. Lives in `avl` rather
 * than `observability` because it queries AVL/prediction repositories directly — `observability`
 * itself stays a leaf package that only ever records what its callers tell it.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class AvlMetricsGaugeRefresher(
    private val feeds: AvlFeedRepository,
    private val reports: AvlReportRowRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val contextFactory: AvlMatchContextFactory,
    private val predictProps: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${transittrack.observability.gauge-refresh-ms:30000}")
    @SchedulerLock(name = "metrics-gauge-avl-queue", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    fun refreshAvlQueue() {
        runCatching {
            val pending = reports.pendingMetricsByFeed().associateBy { it.feedId }
            feeds.findAllEnabled().forEach { feed ->
                val stats = pending[feed.id!!]
                metrics.avlQueue(feed.code, stats?.pendingCount ?: 0, stats?.oldestCreatedAt)
            }
        }.onFailure { log.debug("failed to refresh AVL queue metrics", it) }
    }

    @Scheduled(fixedDelayString = "\${transittrack.observability.gauge-refresh-ms:30000}")
    @SchedulerLock(name = "metrics-gauge-prediction-queue", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    fun refreshPredictionQueue() {
        if (!predictProps.enabled) return
        runCatching {
            val pending = vehicleMatches.pendingPredictionMetricsByFeed().associateBy { it.feedId }
            feeds.findAllEnabled().forEach { feed ->
                val stats = pending[feed.id!!]
                metrics.predictionQueue(feed.code, stats?.pendingCount ?: 0, stats?.oldestCreatedAt)
            }
        }.onFailure { log.debug("failed to refresh prediction queue metrics", it) }
    }

    /**
     * Flags a feed whose active revision has zero GTFS services running today - see
     * [TransitTrackMetrics.avlActiveServices] for why that silently tanks the match rate.
     */
    @Scheduled(fixedDelayString = "\${transittrack.observability.gauge-refresh-ms:30000}")
    @SchedulerLock(name = "metrics-gauge-active-services", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    fun refreshActiveServiceCalendar() {
        runCatching {
            feeds.findAllEnabled().forEach { feed ->
                val ctx = contextFactory.open(feed) ?: return@forEach
                val serviceDate = Instant.now().atZone(ctx.zone).toLocalDate()
                metrics.avlActiveServices(feed.code, ctx.activeServiceIds(serviceDate).size)
            }
        }.onFailure { log.debug("failed to refresh active-service-calendar gauge", it) }
    }
}
