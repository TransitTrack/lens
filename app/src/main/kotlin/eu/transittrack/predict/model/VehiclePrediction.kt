package eu.transittrack.predict.model

import java.time.Instant
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable
import eu.transittrack.predict.PredictionAlgorithm

class VehiclePrediction(
    var feedCode: String,
    var vehicleId: String,
    var tripRowId: Long,
    var blockPk: Long?,
    var tripPatternId: Long,
    var stopPathIndex: Int,
    var algorithm: PredictionAlgorithm,
    var predictedArrivalTs: Instant?,
    var predictedDepartureTs: Instant?,
    var actualArrivalTs: Instant?,
    var actualDepartureTs: Instant?,
    var confidenceSec: Int?,
    var computedAt: Instant,
    var sourceReportTs: Instant? = computedAt,
    var serviceDate: LocalDate? = null,
    var actualReportGapSec: Int? = null,
    var rangeLowSec: Int? = null,
    var rangeHighSec: Int? = null,
    var lateProbability: Double? = null,
    var late: Boolean? = null,
    var id: Long? = null,
)

object VehiclePredictionTable : RowTable<VehiclePrediction>("vehicle_prediction") {
    val id = long("id").autoIncrement(Sequence("vehicle_prediction_seq", startWith = 1, incrementBy = 50))
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_vehicle_prediction_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val tripRowId = long("trip_row_id")
    val blockPk = long("block_pk").nullable()
    val tripPatternId = long("trip_pattern_id")
    val stopPathIndex = integer("stop_path_index")
    val algorithm = shortOrdinal<PredictionAlgorithm>("algorithm")
    val predictedArrivalTs = timestampTz("predicted_arrival_ts").nullable()
    val predictedDepartureTs = timestampTz("predicted_departure_ts").nullable()
    val actualArrivalTs = timestampTz("actual_arrival_ts").nullable()
    val actualDepartureTs = timestampTz("actual_departure_ts").nullable()
    val confidenceSec = integer("confidence_sec").nullable()
    val computedAt = timestampTz("computed_at")
    val sourceReportTs = timestampTz("source_report_ts").nullable()
    val serviceDate = date("service_date").nullable()
    val actualReportGapSec = integer("actual_report_gap_sec").nullable()
    val rangeLowSec = integer("range_low_sec").nullable()
    val rangeHighSec = integer("range_high_sec").nullable()
    val lateProbability = double("late_probability").nullable()
    val late = bool("late").nullable()

    override val primaryKey = PrimaryKey(id, name = "vehicle_prediction_pkey")

    init {
        index("ix_vehicle_prediction_computed", false, computedAt)
        index("ix_vehicle_prediction_pattern_stop", false, tripPatternId, stopPathIndex, algorithm)
        index("ix_vehicle_prediction_vehicle", false, feedCode, vehicleId, tripRowId)
        uniqueIndex("uq_vehicle_prediction_key", feedCode, vehicleId, tripRowId, stopPathIndex, algorithm)
    }

    override fun read(row: ResultRow) =
        VehiclePrediction(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            tripRowId = row[tripRowId],
            blockPk = row[blockPk],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            algorithm = row[algorithm],
            predictedArrivalTs = row[predictedArrivalTs],
            predictedDepartureTs = row[predictedDepartureTs],
            actualArrivalTs = row[actualArrivalTs],
            actualDepartureTs = row[actualDepartureTs],
            confidenceSec = row[confidenceSec],
            computedAt = row[computedAt],
            sourceReportTs = row[sourceReportTs],
            serviceDate = row[serviceDate],
            actualReportGapSec = row[actualReportGapSec],
            rangeLowSec = row[rangeLowSec],
            rangeHighSec = row[rangeHighSec],
            lateProbability = row[lateProbability],
            late = row[late],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: VehiclePrediction,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[tripRowId] = row.tripRowId
        s[blockPk] = row.blockPk
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[algorithm] = row.algorithm
        s[predictedArrivalTs] = row.predictedArrivalTs
        s[predictedDepartureTs] = row.predictedDepartureTs
        s[actualArrivalTs] = row.actualArrivalTs
        s[actualDepartureTs] = row.actualDepartureTs
        s[confidenceSec] = row.confidenceSec
        s[computedAt] = row.computedAt
        s[sourceReportTs] = row.sourceReportTs
        s[serviceDate] = row.serviceDate
        s[actualReportGapSec] = row.actualReportGapSec
        s[rangeLowSec] = row.rangeLowSec
        s[rangeHighSec] = row.rangeHighSec
        s[lateProbability] = row.lateProbability
        s[late] = row.late
    }
}

@Repository
@Transactional
class VehiclePredictionRepository {
    /** A null [algorithm] matches every algorithm. */
    fun findByVehicleTrip(
        feedCode: String,
        vehicleId: String,
        tripRowId: Long,
        algorithm: PredictionAlgorithm? = null,
    ): List<VehiclePrediction> =
        VehiclePredictionTable
            .selectAll()
            .where {
                listOfNotNull<Op<Boolean>>(
                    VehiclePredictionTable.feedCode eq feedCode,
                    VehiclePredictionTable.vehicleId eq vehicleId,
                    VehiclePredictionTable.tripRowId eq tripRowId,
                    algorithm?.let { VehiclePredictionTable.algorithm eq it },
                ).reduce { a, b -> a and b }
            }.orderBy(VehiclePredictionTable.stopPathIndex)
            .map(VehiclePredictionTable::read)

    fun findByPatternStopAlgorithm(
        feedCode: String,
        tripPatternId: Long,
        stopPathIndex: Int,
        algorithm: PredictionAlgorithm,
    ): List<VehiclePrediction> =
        VehiclePredictionTable
            .selectAll()
            .where {
                (VehiclePredictionTable.feedCode eq feedCode) and (VehiclePredictionTable.tripPatternId eq tripPatternId) and
                    (VehiclePredictionTable.stopPathIndex eq stopPathIndex) and (VehiclePredictionTable.algorithm eq algorithm)
            }.map(VehiclePredictionTable::read)

    fun findAll(): List<VehiclePrediction> = VehiclePredictionTable.selectAll().map(VehiclePredictionTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: VehiclePrediction): VehiclePrediction {
        val id = row.id
        if (id == null) {
            row.id = VehiclePredictionTable.insert { VehiclePredictionTable.bind(it, row) }[VehiclePredictionTable.id]
        } else {
            VehiclePredictionTable.update({ VehiclePredictionTable.id eq id }) { VehiclePredictionTable.bind(it, row) }
        }
        return row
    }

    fun saveAll(rows: Iterable<VehiclePrediction>): List<VehiclePrediction> = rows.map(::save)

    fun deleteById(id: Long) {
        VehiclePredictionTable.deleteWhere { VehiclePredictionTable.id eq id }
    }

    fun deleteAll() {
        VehiclePredictionTable.deleteAll()
    }
}
