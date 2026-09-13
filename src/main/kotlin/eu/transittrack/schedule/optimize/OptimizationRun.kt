package eu.transittrack.schedule.optimize

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.springframework.data.jpa.repository.JpaRepository

enum class OptimizationRunState { QUEUED, RUNNING, SUCCEEDED, FAILED }

enum class OptimizationRecommendationKind { STOP_TIME, TRIP_SHIFT }

data class OptimizationRun(
    val feedId: Long,
    val revisionId: Long,
    val observedFrom: Instant,
    val observedTo: Instant,
    val minimumSamples: Int,
    val state: OptimizationRunState,
) {
    companion object {
        fun newRun(
            feedId: Long,
            revisionId: Long,
            observedFrom: Instant,
            observedTo: Instant,
            minimumSamples: Int,
        ): OptimizationRun {
            require(observedFrom < observedTo) { "observedFrom must be before observedTo" }
            require(minimumSamples > 0) { "minimumSamples must be positive" }
            return OptimizationRun(feedId, revisionId, observedFrom, observedTo, minimumSamples, OptimizationRunState.QUEUED)
        }
    }
}

@Entity
@Table(name = "schedule_optimization_run")
class OptimizationRunRow(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "observed_from", nullable = false) var observedFrom: Instant,
    @Column(name = "observed_to", nullable = false) var observedTo: Instant,
    @Column(name = "minimum_samples", nullable = false) var minimumSamples: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var state: OptimizationRunState = OptimizationRunState.QUEUED,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

interface OptimizationRunRepository : JpaRepository<OptimizationRunRow, Long>

@Entity
@Table(name = "schedule_optimization_recommendation")
class OptimizationRecommendationRow(
    @Column(name = "run_id", nullable = false) var runId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var kind: OptimizationRecommendationKind,
    @Column(name = "sample_count", nullable = false) var sampleCount: Int,
    @Column(name = "delta_sec", nullable = false) var deltaSec: Int,
    @Column(nullable = false) var reason: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

interface OptimizationRecommendationRepository : JpaRepository<OptimizationRecommendationRow, Long>
