package eu.transittrack.observability

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository

/** Refreshes database-derived queue gauges outside Prometheus's scrape thread. */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class TransitTrackMetricsGaugeRefresher(
    private val feeds: AvlFeedRepository,
    private val reports: AvlReportRowRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val metrics: TransitTrackMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${transittrack.observability.gauge-refresh-ms:30000}")
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
    fun refreshPredictionQueue() {
        runCatching {
            val pending = vehicleMatches.pendingPredictionMetricsByFeed().associateBy { it.feedId }
            feeds.findAllEnabled().forEach { feed ->
                val stats = pending[feed.id!!]
                metrics.predictionQueue(feed.code, stats?.pendingCount ?: 0, stats?.oldestCreatedAt)
            }
        }.onFailure { log.debug("failed to refresh prediction queue metrics", it) }
    }
}
