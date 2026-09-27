package eu.transittrack.predict.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

import eu.transittrack.predict.PredictionAlgorithm

@Entity
@Table(name = "vehicle_prediction")
class VehiclePrediction(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "block_pk") var blockPk: Long?,
    @Column(name = "trip_pattern_id", nullable = false) var tripPatternId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(nullable = false) var algorithm: PredictionAlgorithm,
    @Column(name = "predicted_arrival_ts") var predictedArrivalTs: Instant?,
    @Column(name = "predicted_departure_ts") var predictedDepartureTs: Instant?,
    @Column(name = "actual_arrival_ts") var actualArrivalTs: Instant?,
    @Column(name = "actual_departure_ts") var actualDepartureTs: Instant?,
    @Column(name = "confidence_sec") var confidenceSec: Int?,
    @Column(name = "computed_at", nullable = false) var computedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehiclePredictionSeq")
    @SequenceGenerator(name = "vehiclePredictionSeq", sequenceName = "vehicle_prediction_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface VehiclePredictionRepository : JpaRepository<VehiclePrediction, Long> {
    fun findByFeedIdAndVehicleIdAndTripRowIdOrderByStopPathIndex(
        feedId: Long,
        vehicleId: String,
        tripRowId: Long,
    ): List<VehiclePrediction>

    fun findByTripPatternIdAndStopPathIndexAndAlgorithm(
        tripPatternId: Long,
        stopPathIndex: Int,
        algorithm: PredictionAlgorithm,
    ): List<VehiclePrediction>
}
