package eu.transittrack.avl.model

import java.time.Instant

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.jsonb
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.ProjectJson
import eu.transittrack.avl.ingest.AvlFormat
import eu.transittrack.avl.match.AvlAssignmentMode
import eu.transittrack.db.RowTable
import eu.transittrack.predict.PredictionAlgorithm

enum class AvlFeedSourceKind { CONFIG, API }

class AvlFeed(
    var code: String,
    var name: String,
    var gtfsFeedCode: String,
    var url: String,
    var format: AvlFormat,
    var pollIntervalSec: Int,
    var assignmentMode: AvlAssignmentMode,
    var predictionAlgorithms: List<PredictionAlgorithm> = listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE),
    var enabled: Boolean,
    var headers: Map<String, String>?,
    var source: AvlFeedSourceKind,
    var lastPollAt: Instant? = null,
    var lastPollStatus: String? = null,
    var lastPollReportCount: Int? = null,
    var createdAt: Instant,
    var updatedAt: Instant,
    var id: Long? = null,
)

object AvlFeedTable : RowTable<AvlFeed>("avl_feed") {
    val id = long("id").autoIncrement(Sequence("avl_feed_seq", startWith = 1, incrementBy = 50))
    val code = varchar("code", 64).uniqueIndex("avl_feed_code_key")
    val name = varchar("name", 255)
    val gtfsFeedCode = varchar("gtfs_feed_code", 64)
    val url = text("url")
    val format = shortOrdinal<AvlFormat>("format")
    val pollIntervalSec = integer("poll_interval_sec")
    val assignmentMode = shortOrdinal<AvlAssignmentMode>("assignment_mode")
    val predictionAlgorithms =
        jsonb<List<PredictionAlgorithm>>(
            "prediction_algorithms",
            { ProjectJson.encodeToString(it.map(PredictionAlgorithm::name)) },
            { ProjectJson.decodeFromString<List<String>>(it).map(PredictionAlgorithm::valueOf) },
        ).default(listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE))
    val enabled = bool("enabled").default(true)
    val headers =
        jsonb<Map<String, String>>("headers", ProjectJson).nullable()
    val feedSource = enumerationByName<AvlFeedSourceKind>("source", 16)
    val lastPollAt = timestampTz("last_poll_at").nullable()
    val lastPollStatus = varchar("last_poll_status", 255).nullable()
    val lastPollReportCount = integer("last_poll_report_count").nullable()
    val createdAt = timestampTz("created_at")
    val updatedAt = timestampTz("updated_at")

    override val primaryKey = PrimaryKey(id, name = "avl_feed_pkey")

    override fun read(row: ResultRow) =
        AvlFeed(
            code = row[code],
            name = row[name],
            gtfsFeedCode = row[gtfsFeedCode],
            url = row[url],
            format = row[format],
            pollIntervalSec = row[pollIntervalSec],
            assignmentMode = row[assignmentMode],
            predictionAlgorithms = row[predictionAlgorithms],
            enabled = row[enabled],
            headers = row[headers],
            source = row[feedSource],
            lastPollAt = row[lastPollAt],
            lastPollStatus = row[lastPollStatus],
            lastPollReportCount = row[lastPollReportCount],
            createdAt = row[createdAt],
            updatedAt = row[updatedAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: AvlFeed,
    ) {
        s[code] = row.code
        s[name] = row.name
        s[gtfsFeedCode] = row.gtfsFeedCode
        s[url] = row.url
        s[format] = row.format
        s[pollIntervalSec] = row.pollIntervalSec
        s[assignmentMode] = row.assignmentMode
        s[predictionAlgorithms] = row.predictionAlgorithms
        s[enabled] = row.enabled
        s[headers] = row.headers
        s[feedSource] = row.source
        s[lastPollAt] = row.lastPollAt
        s[lastPollStatus] = row.lastPollStatus
        s[lastPollReportCount] = row.lastPollReportCount
        s[createdAt] = row.createdAt
        s[updatedAt] = row.updatedAt
    }
}

@Repository
@Transactional
class AvlFeedRepository {
    fun findByCode(code: String): AvlFeed? =
        AvlFeedTable
            .selectAll()
            .where { AvlFeedTable.code eq code }
            .singleOrNull()
            ?.let(AvlFeedTable::read)

    fun findAllEnabled(): List<AvlFeed> =
        AvlFeedTable
            .selectAll()
            .where { AvlFeedTable.enabled eq true }
            .map(AvlFeedTable::read)

    /**
     * Used by [eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline] to translate an
     * optimization run's `gtfs_feed` code into the `avl_feed` code(s) whose crossings/prediction-accuracy
     * evidence belong to it — `avl_stop_crossing`/`prediction_accuracy` are keyed by `avl_feed.code`,
     * not `gtfs_feed.code`.
     */
    fun findByGtfsFeedCode(gtfsFeedCode: String): List<AvlFeed> =
        AvlFeedTable
            .selectAll()
            .where { AvlFeedTable.gtfsFeedCode eq gtfsFeedCode }
            .map(AvlFeedTable::read)

    fun findById(id: Long): AvlFeed? =
        AvlFeedTable
            .selectAll()
            .where { AvlFeedTable.id eq id }
            .singleOrNull()
            ?.let(AvlFeedTable::read)

    fun findAll(): List<AvlFeed> = AvlFeedTable.selectAll().map(AvlFeedTable::read)

    fun count(): Long = AvlFeedTable.selectAll().count()

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: AvlFeed): AvlFeed {
        val id = row.id
        if (id == null) {
            row.id = AvlFeedTable.insert { AvlFeedTable.bind(it, row) }[AvlFeedTable.id]
        } else {
            AvlFeedTable.update({ AvlFeedTable.id eq id }) { AvlFeedTable.bind(it, row) }
        }
        return row
    }

    fun delete(row: AvlFeed) {
        val id = row.id ?: return
        deleteById(id)
    }

    fun deleteById(id: Long) {
        AvlFeedTable.deleteWhere { AvlFeedTable.id eq id }
    }

    fun deleteAll() {
        AvlFeedTable.deleteAll()
    }
}
