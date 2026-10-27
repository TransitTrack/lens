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

import eu.transittrack.predict.PredictionAlgorithm

/**
 * A prediction kept for accuracy scoring: the first one made for a stop (per algorithm) within
 * [horizonSec] of its predicted time. Scored into `prediction_accuracy` and deleted once the vehicle
 * passes the stop. [computedAt] is the AVL report time the prediction was made at.
 */
@Entity
@Table(name = "prediction_checkpoint")
class PredictionCheckpoint(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "vehicle_id", nullable = false) var vehicleId: String,
    @Column(name = "trip_row_id", nullable = false) var tripRowId: Long,
    @Column(name = "stop_path_index", nullable = false) var stopPathIndex: Int,
    @Enumerated @Column(nullable = false) var algorithm: PredictionAlgorithm,
    @Column(name = "horizon_sec", nullable = false) var horizonSec: Int,
    @Column(name = "predicted_ts", nullable = false) var predictedTs: Instant,
    @Column(name = "computed_at", nullable = false) var computedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "predictionCheckpointSeq")
    @SequenceGenerator(name = "predictionCheckpointSeq", sequenceName = "prediction_checkpoint_seq", allocationSize = 50)
    var id: Long? = null,
)
