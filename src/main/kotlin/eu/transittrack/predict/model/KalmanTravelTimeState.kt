package eu.transittrack.predict.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "kalman_travel_time_state")
class KalmanTravelTimeState(
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "estimate_sec", nullable = false) var estimateSec: Double,
    @Column(name = "error_variance", nullable = false) var errorVariance: Double,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface KalmanTravelTimeStateRepository : JpaRepository<KalmanTravelTimeState, Long> {
    fun findByTripPatternIdAndStopPathIndex(
        tripPatternId: Long,
        stopPathIndex: Int,
    ): KalmanTravelTimeState?
}
