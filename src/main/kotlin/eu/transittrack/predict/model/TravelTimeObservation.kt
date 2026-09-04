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
@Table(name = "travel_time_observation")
class TravelTimeObservation(
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Column(name = "sample_count", nullable = false) var sampleCount: Long,
    @Column(name = "mean_sec", nullable = false) var meanSec: Double,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
)

@Repository
interface TravelTimeObservationRepository : JpaRepository<TravelTimeObservation, Long> {
    fun findByTripPatternIdAndStopPathIndex(
        tripPatternId: Long,
        stopPathIndex: Int,
    ): TravelTimeObservation?
}
