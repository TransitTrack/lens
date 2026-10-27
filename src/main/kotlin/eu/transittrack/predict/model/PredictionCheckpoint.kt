package eu.transittrack.predict.model

import java.time.Instant
import java.time.LocalDate
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import eu.transittrack.predict.PredictionAlgorithm

/**
 * A shared report-time snapshot, retained independently of an algorithm's ETA. A null [predictedTs]
 * records an unavailable prediction, so scoring can measure coverage as well as error.
 */
@Entity
@Table(name = "prediction_checkpoint")
class PredictionCheckpoint(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(nullable = false) var algorithm: PredictionAlgorithm,
    @Column(name = "predicted_ts") var predictedTs: Instant?,
    @Column(name = "computed_at", nullable = false) var computedAt: Instant,
    @Column(name = "sample_slot", nullable = false) var sampleSlot: Instant,
    @Column(name = "confidence_sec") var confidenceSec: Int?,
    @Column(name = "service_date") var serviceDate: LocalDate?,
    @Column(name = "produced_at") var producedAt: Instant? = computedAt,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "predictionCheckpointSeq")
    @SequenceGenerator(name = "predictionCheckpointSeq", sequenceName = "prediction_checkpoint_seq", allocationSize = 50)
    var id: Long? = null,
)
