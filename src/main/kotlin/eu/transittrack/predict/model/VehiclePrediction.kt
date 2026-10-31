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

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import eu.transittrack.predict.PredictionAlgorithm

@Entity
@Table(name = "vehicle_prediction")
class VehiclePrediction(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
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
    @Column(name = "source_report_ts") var sourceReportTs: Instant? = computedAt,
    @Column(name = "service_date") var serviceDate: LocalDate? = null,
    @Column(name = "actual_report_gap_sec") var actualReportGapSec: Int? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vehiclePredictionSeq")
    @SequenceGenerator(name = "vehiclePredictionSeq", sequenceName = "vehicle_prediction_seq", allocationSize = 50)
    var id: Long? = null,
)

@Repository
interface VehiclePredictionRepository : JpaRepository<VehiclePrediction, Long> {
    @Query(
        "select e from VehiclePrediction e where e.feedCode = :feedCode and e.vehicleId = :vehicleId " +
            "and e.tripRowId = :tripRowId and (:algorithm is null or e.algorithm = :algorithm) " +
            "order by e.stopPathIndex",
    )
    fun findByVehicleTrip(
        @Param("feedCode") feedCode: String,
        @Param("vehicleId") vehicleId: String,
        @Param("tripRowId") tripRowId: Long,
        @Param("algorithm") algorithm: PredictionAlgorithm? = null,
    ): List<VehiclePrediction>

    @Query(
        "select e from VehiclePrediction e where e.feedCode = :feedCode and e.tripPatternId = :tripPatternId " +
            "and e.stopPathIndex = :stopPathIndex and e.algorithm = :algorithm",
    )
    fun findByPatternStopAlgorithm(
        @Param("feedCode") feedCode: String,
        @Param("tripPatternId") tripPatternId: Long,
        @Param("stopPathIndex") stopPathIndex: Int,
        @Param("algorithm") algorithm: PredictionAlgorithm,
    ): List<VehiclePrediction>
}
