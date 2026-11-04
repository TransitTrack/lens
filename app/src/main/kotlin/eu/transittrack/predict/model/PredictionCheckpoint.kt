package eu.transittrack.predict.model

import java.time.Instant
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable
import eu.transittrack.predict.PredictionAlgorithm

/**
 * A shared report-time snapshot, retained independently of an algorithm's ETA. A null [predictedTs]
 * records an unavailable prediction, so scoring can measure coverage as well as error.
 */
class PredictionCheckpoint(
    var feedCode: String,
    var vehicleId: String,
    var tripRowId: Long,
    var stopPathIndex: Int,
    var algorithm: PredictionAlgorithm,
    var predictedTs: Instant?,
    var computedAt: Instant,
    var sampleSlot: Instant,
    var confidenceSec: Int?,
    var serviceDate: LocalDate?,
    var producedAt: Instant? = computedAt,
    var rangeLowSec: Int? = null,
    var rangeHighSec: Int? = null,
    var lateProbability: Double? = null,
    var late: Boolean? = null,
    var id: Long? = null,
)

object PredictionCheckpointTable : RowTable<PredictionCheckpoint>("prediction_checkpoint") {
    /** Named on its own so an `INSERT ... SELECT`, which Exposed doesn't fill `id` for, can take its `nextval`. */
    val idSequence = Sequence("prediction_checkpoint_seq", startWith = 1, incrementBy = 50)
    val id = long("id").autoIncrement(idSequence)
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_prediction_checkpoint_feed",
        )
    val vehicleId = varchar("vehicle_id", 255)
    val tripRowId = long("trip_row_id")
    val stopPathIndex = integer("stop_path_index")
    val algorithm = shortOrdinal<PredictionAlgorithm>("algorithm")
    val predictedTs = timestampTz("predicted_ts").nullable()
    val computedAt = timestampTz("computed_at")
    val sampleSlot = timestampTz("sample_slot")
    val confidenceSec = integer("confidence_sec").nullable()
    val serviceDate = date("service_date").nullable()
    val producedAt = timestampTz("produced_at").nullable()
    val rangeLowSec = integer("range_low_sec").nullable()
    val rangeHighSec = integer("range_high_sec").nullable()
    val lateProbability = double("late_probability").nullable()
    val late = bool("late").nullable()

    override val primaryKey = PrimaryKey(id, name = "prediction_checkpoint_pkey")

    init {
        index("ix_prediction_checkpoint_computed", false, computedAt)
        uniqueIndex("uq_prediction_checkpoint_key", feedCode, vehicleId, tripRowId, stopPathIndex, algorithm, sampleSlot)
    }

    override fun read(row: ResultRow) =
        PredictionCheckpoint(
            feedCode = row[feedCode],
            vehicleId = row[vehicleId],
            tripRowId = row[tripRowId],
            stopPathIndex = row[stopPathIndex],
            algorithm = row[algorithm],
            predictedTs = row[predictedTs],
            computedAt = row[computedAt],
            sampleSlot = row[sampleSlot],
            confidenceSec = row[confidenceSec],
            serviceDate = row[serviceDate],
            producedAt = row[producedAt],
            rangeLowSec = row[rangeLowSec],
            rangeHighSec = row[rangeHighSec],
            lateProbability = row[lateProbability],
            late = row[late],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: PredictionCheckpoint,
    ) {
        s[feedCode] = row.feedCode
        s[vehicleId] = row.vehicleId
        s[tripRowId] = row.tripRowId
        s[stopPathIndex] = row.stopPathIndex
        s[algorithm] = row.algorithm
        s[predictedTs] = row.predictedTs
        s[computedAt] = row.computedAt
        s[sampleSlot] = row.sampleSlot
        s[confidenceSec] = row.confidenceSec
        s[serviceDate] = row.serviceDate
        s[producedAt] = row.producedAt
        s[rangeLowSec] = row.rangeLowSec
        s[rangeHighSec] = row.rangeHighSec
        s[lateProbability] = row.lateProbability
        s[late] = row.late
    }
}
