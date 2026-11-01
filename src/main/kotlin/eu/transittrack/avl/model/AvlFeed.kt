package eu.transittrack.avl.model

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.db.RowTable
import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.predict.PredictionAlgorithm

enum class AvlFeedSourceKind { CONFIG, API }

@Entity
@Table(name = "avl_feed")
class AvlFeed(
    @Column(nullable = false, unique = true) var code: String,
    @Column(nullable = false) var name: String,
    @Column(name = "gtfs_feed_code", nullable = false) var gtfsFeedCode: String,
    @Column(nullable = false) var url: String,
    @Enumerated @Column(nullable = false) var format: AvlFormat,
    @Column(name = "poll_interval_sec", nullable = false) var pollIntervalSec: Int,
    @Enumerated
    @Column(name = "assignment_mode", nullable = false)
    var assignmentMode: AvlAssignmentMode,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prediction_algorithms", nullable = false)
    var predictionAlgorithms: List<PredictionAlgorithm> = listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE),
    @Column(nullable = false) var enabled: Boolean,
    @JdbcTypeCode(SqlTypes.JSON) @Column var headers: Map<String, String>?,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var source: AvlFeedSourceKind,
    @Column(name = "last_poll_at") var lastPollAt: Instant? = null,
    @Column(name = "last_poll_status") var lastPollStatus: String? = null,
    @Column(name = "last_poll_report_count") var lastPollReportCount: Int? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "avlFeedSeq")
    @SequenceGenerator(name = "avlFeedSeq", sequenceName = "avl_feed_seq", allocationSize = 50)
    var id: Long? = null,
)

private val jsonMapper: JsonMapper = JsonMapper.builder().build()

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
            { jsonMapper.writeValueAsString(it) },
            { jsonMapper.readValue(it, object : TypeReference<List<PredictionAlgorithm>>() {}) },
        ).default(listOf(PredictionAlgorithm.SCHEDULE_ADHERENCE))
    val enabled = bool("enabled").default(true)
    val headers =
        jsonb<Map<String, String>>(
            "headers",
            { jsonMapper.writeValueAsString(it) },
            { jsonMapper.readValue(it, object : TypeReference<Map<String, String>>() {}) },
        ).nullable()
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
@ExposedBridge
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
