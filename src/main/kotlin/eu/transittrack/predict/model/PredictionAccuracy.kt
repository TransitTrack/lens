package eu.transittrack.predict.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

import eu.transittrack.predict.PredictionAlgorithm

@Entity
@Table(name = "prediction_accuracy")
class PredictionAccuracy(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(nullable = false) var algorithm: PredictionAlgorithm,
    @Column(name = "predicted_ts", nullable = false) var predictedTs: Instant,
    @Column(name = "actual_ts", nullable = false) var actualTs: Instant,
    @Column(name = "error_sec", nullable = false) var errorSec: Int,
    @Column(name = "abs_error_sec", nullable = false) var absErrorSec: Int,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface PredictionAccuracyRepository : JpaRepository<PredictionAccuracy, Long> {
    fun findByFeedIdAndAlgorithmAndCreatedAtAfter(
        feedId: Long,
        algorithm: PredictionAlgorithm,
        since: Instant,
    ): List<PredictionAccuracy>

    /**
     * Bounded evidence lookup for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): scoped to one feed, one
     * stop-path index, a set of trip rows, and the run's observed date range — never all feeds or
     * algorithms.
     */
    @Query(
        "select p from PredictionAccuracy p where p.feedId in :feedIds and p.tripRowId in :tripRowIds " +
            "and p.stopPathIndex = :stopPathIndex and p.createdAt >= :from and p.createdAt < :to",
    )
    fun findForTargets(
        feedIds: Collection<Long>,
        tripRowIds: Collection<Long>,
        stopPathIndex: Int,
        from: Instant,
        to: Instant,
    ): List<PredictionAccuracy>
}
