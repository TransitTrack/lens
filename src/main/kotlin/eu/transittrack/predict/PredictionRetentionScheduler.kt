package eu.transittrack.predict

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.predict.model.AvlStopCrossingTable
import eu.transittrack.predict.model.KalmanTravelTimeStateTable
import eu.transittrack.predict.model.LearnerBucketColumns
import eu.transittrack.predict.model.PredictionAccuracyTable
import eu.transittrack.predict.model.PredictionCheckpointTable
import eu.transittrack.predict.model.PredictionFrequencyRunTable
import eu.transittrack.predict.model.TravelTimeObservationTable
import eu.transittrack.predict.model.VehiclePredictionTable
import eu.transittrack.schedule.model.TripPatternTable
import eu.transittrack.util.observability.TransitTrackMetrics

/**
 * Age-based prune of prediction tables, plus a sweep of learner-table rows left orphaned once no
 * revision still stored for their GTFS feed has a pattern with their `pattern_key` (the route,
 * shape or stop list changed). A revision swap that keeps a pattern keeps its learning. Each of the
 * seven deletes commits on its own (none of them runs inside a caller transaction).
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
@ConditionalOnRole(Role.FEED_PROCESSOR)
class PredictionRetentionScheduler(
    private val retention: PredictionRetentionRepository,
    props: PredictProperties,
    private val metrics: TransitTrackMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.retention

    data class PruneCounts(
        val predictions: Int,
        val checkpoints: Int,
        val accuracy: Int,
        val crossings: Int,
        val orphanObservations: Int,
        val orphanKalman: Int,
        val frequencyRuns: Int = 0,
    )

    @Scheduled(cron = "\${transittrack.predict.retention.sweep-cron}")
    @SchedulerLock(name = "predict-retention-prune", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    fun prune() {
        val start = Instant.now()
        val result = runCatching { prune(start) }
        result.onSuccess { c ->
            log.info(
                "predict retention: pruned {} predictions, {} checkpoints, {} accuracy, {} crossings, " +
                    "{} orphan observations, {} orphan kalman, {} frequency runs",
                c.predictions, c.checkpoints, c.accuracy, c.crossings, c.orphanObservations, c.orphanKalman, c.frequencyRuns,
            )
        }
        metrics.scheduledJobFinished(
            job = "predict_retention_prune",
            outcome = if (result.isSuccess) TransitTrackMetrics.Outcome.SUCCESS else TransitTrackMetrics.Outcome.FAILED,
            elapsed = Duration.between(start, Instant.now()),
            items = result.getOrNull()?.let {
                (it.predictions + it.checkpoints + it.accuracy + it.crossings + it.orphanObservations + it.orphanKalman + it.frequencyRuns)
                    .toLong()
            },
        )
        result.getOrThrow()
    }

    fun prune(now: Instant): PruneCounts {
        val predictionCutoff = now.minus(cfg.predictionHours, ChronoUnit.HOURS)
        val predictions = retention.deletePredictionsComputedBefore(predictionCutoff)
        // Never scored: the vehicle didn't reach the stop on that trip, or wasn't seen passing it.
        val checkpoints = retention.deleteCheckpointsComputedBefore(predictionCutoff)
        val accuracy = retention.deleteAccuracyCreatedBefore(now.minus(cfg.accuracyDays, ChronoUnit.DAYS))
        val crossings = retention.deleteCrossingsObservedBefore(now.minus(cfg.rawCrossingDays, ChronoUnit.DAYS))
        val orphanObservations = retention.deleteOrphanObservations()
        val orphanKalman = retention.deleteOrphanKalmanStates()
        val frequencyRuns = retention.deleteFrequencyRunsReportedBefore(predictionCutoff)
        return PruneCounts(predictions, checkpoints, accuracy, crossings, orphanObservations, orphanKalman, frequencyRuns)
    }
}

/**
 * The retention deletes of [PredictionRetentionScheduler], one bridged call (and so, outside a
 * Spring transaction, one transaction) each.
 */
@Repository
@Transactional
class PredictionRetentionRepository {
    fun deletePredictionsComputedBefore(cutoff: Instant): Int = VehiclePredictionTable.deleteWhere { computedAt less cutoff }

    fun deleteCheckpointsComputedBefore(cutoff: Instant): Int = PredictionCheckpointTable.deleteWhere { computedAt less cutoff }

    fun deleteAccuracyCreatedBefore(cutoff: Instant): Int = PredictionAccuracyTable.deleteWhere { createdAt less cutoff }

    fun deleteCrossingsObservedBefore(cutoff: Instant): Int = AvlStopCrossingTable.deleteWhere { observedAt less cutoff }

    fun deleteFrequencyRunsReportedBefore(cutoff: Instant): Int = PredictionFrequencyRunTable.deleteWhere { lastReportTs less cutoff }

    fun deleteOrphanObservations(): Int = deleteOrphans(TravelTimeObservationTable)

    fun deleteOrphanKalmanStates(): Int = deleteOrphans(KalmanTravelTimeStateTable)

    /** Rows whose (GTFS feed code, pattern key) no stored revision's trip pattern has any more. */
    private fun <L> deleteOrphans(table: L): Int where L : Table, L : LearnerBucketColumns =
        table.deleteWhere {
            notExists(
                TripPatternTable
                    .join(GtfsRevisionTable, JoinType.INNER, onColumn = GtfsRevisionTable.id, otherColumn = TripPatternTable.revisionId)
                    .join(GtfsFeedTable, JoinType.INNER, onColumn = GtfsFeedTable.id, otherColumn = GtfsRevisionTable.feedId)
                    .select(intLiteral(1))
                    .where { (GtfsFeedTable.code eq table.gtfsFeedCode) and (TripPatternTable.patternKey eq table.patternKey) },
            )
        }
}
