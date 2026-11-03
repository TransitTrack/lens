package eu.transittrack.predict.model

import java.time.Instant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeedTable
import eu.transittrack.db.RowTable
import eu.transittrack.predict.PredictionAlgorithm

/** A serialized trained model for one feed and algorithm. */
data class PredictionModelRow(
    val feedCode: String,
    val algorithm: PredictionAlgorithm,
    val trainedAt: Instant,
    val sampleCount: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PredictionModelRow &&
                    feedCode == other.feedCode &&
                    algorithm == other.algorithm &&
                    trainedAt == other.trainedAt &&
                    sampleCount == other.sampleCount &&
                    payload.contentEquals(other.payload)
            )

    override fun hashCode(): Int {
        var h = feedCode.hashCode()
        h = 31 * h + algorithm.hashCode()
        h = 31 * h + trainedAt.hashCode()
        h = 31 * h + sampleCount
        h = 31 * h + payload.contentHashCode()
        return h
    }
}

object PredictionModelTable : RowTable<PredictionModelRow>("prediction_model") {
    val feedCode =
        varchar("feed_code", 64).references(
            AvlFeedTable.code,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_prediction_model_feed",
        )
    val algorithm = shortOrdinal<PredictionAlgorithm>("algorithm")
    val trainedAt = timestampTz("trained_at")
    val sampleCount = integer("sample_count")
    val payload = binary("payload")

    override val primaryKey = PrimaryKey(feedCode, algorithm, name = "prediction_model_pkey")

    override fun read(row: ResultRow) =
        PredictionModelRow(
            feedCode = row[feedCode],
            algorithm = row[algorithm],
            trainedAt = row[trainedAt],
            sampleCount = row[sampleCount],
            payload = row[payload],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: PredictionModelRow,
    ) {
        s[feedCode] = row.feedCode
        s[algorithm] = row.algorithm
        s[trainedAt] = row.trainedAt
        s[sampleCount] = row.sampleCount
        s[payload] = row.payload
    }
}

@Repository
@Transactional
class PredictionModelRepository {
    fun find(
        feedCode: String,
        algorithm: PredictionAlgorithm,
    ): PredictionModelRow? =
        PredictionModelTable
            .selectAll()
            .where { (PredictionModelTable.feedCode eq feedCode) and (PredictionModelTable.algorithm eq algorithm) }
            .singleOrNull()
            ?.let(PredictionModelTable::read)

    /** Inserts the model, or replaces the one already stored for its feed and algorithm. */
    fun save(row: PredictionModelRow) {
        PredictionModelTable.upsert { PredictionModelTable.bind(it, row) }
    }

    /** When the stored model was trained, without loading its payload. */
    fun trainedAt(
        feedCode: String,
        algorithm: PredictionAlgorithm,
    ): Instant? =
        PredictionModelTable
            .select(PredictionModelTable.trainedAt)
            .where { (PredictionModelTable.feedCode eq feedCode) and (PredictionModelTable.algorithm eq algorithm) }
            .singleOrNull()
            ?.get(PredictionModelTable.trainedAt)
}
