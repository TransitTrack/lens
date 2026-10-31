package eu.transittrack.gtfs.revision

import java.time.Instant
import java.time.LocalDate
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
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.db.RowTable
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.feed.GtfsFeedTable

@Entity
@Table(name = "gtfs_revision")
class GtfsRevision(
    @Column(name = "feed_id", nullable = false) var feedId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: GtfsRevisionStatus,
    @Column(name = "source_url", nullable = false) var sourceUrl: String,
    @Column(name = "content_sha256", length = 64) var contentSha256: String? = null,
    @Column(name = "byte_size") var byteSize: Long? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "files_present") var filesPresent: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "row_counts") var rowCounts: Map<String, Long> = emptyMap(),
    @Column(name = "feed_start_date") var feedStartDate: LocalDate? = null,
    @Column(name = "feed_end_date") var feedEndDate: LocalDate? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_report") var validationReport: String? = null,
    @Column(name = "error_message") var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "activated_at") var activatedAt: Instant? = null,
    @Column(name = "superseded_at") var supersededAt: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(name = "kind", nullable = false)
    var kind: DraftKind = DraftKind.IMPORT,
    @Column(name = "base_revision_id") var baseRevisionId: Long? = null,
    @Column(name = "label") var label: String? = null,
    @Column(name = "derivation_stale", nullable = false) var derivationStale: Boolean = false,
    @Column(name = "deriving", nullable = false) var deriving: Boolean = false,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "last_validation") var lastValidation: String? = null,
    @Column(name = "editor_claim_by") var editorClaimBy: String? = null,
    @Column(name = "editor_claim_expires_at") var editorClaimExpiresAt: Instant? = null,
    @Column(name = "version", nullable = false) var version: Long = 0,
    @Column(name = "created_by") var createdBy: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gtfsRevisionSeq")
    @SequenceGenerator(name = "gtfsRevisionSeq", sequenceName = "gtfs_revision_seq", allocationSize = 50)
    var id: Long? = null,
)

private val jsonMapper: JsonMapper = JsonMapper.builder().build()

object GtfsRevisionTable : RowTable<GtfsRevision>("gtfs_revision") {
    val id = long("id").autoIncrement(Sequence("gtfs_revision_seq", startWith = 1, incrementBy = 50))
    val feedId = long("feed_id")
        .references(GtfsFeedTable.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.NO_ACTION, fkName = "fk_revision_feed")
    val status = enumerationByName<GtfsRevisionStatus>("status", 16)
    val sourceUrl = text("source_url")
    val contentSha256 = varchar("content_sha256", 64).nullable()
    val byteSize = long("byte_size").nullable()
    val filesPresent =
        jsonb<List<String>>(
            "files_present",
            { jsonMapper.writeValueAsString(it) },
            { jsonMapper.readValue(it, object : TypeReference<List<String>>() {}) },
        ).nullable()
    val rowCounts =
        jsonb<Map<String, Long>>(
            "row_counts",
            { jsonMapper.writeValueAsString(it) },
            { jsonMapper.readValue(it, object : TypeReference<Map<String, Long>>() {}) },
        ).nullable()
    val feedStartDate = date("feed_start_date").nullable()
    val feedEndDate = date("feed_end_date").nullable()
    val validationReport = jsonb<String>("validation_report", { it }, { it }).nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
    val activatedAt = timestamp("activated_at").nullable()
    val supersededAt = timestamp("superseded_at").nullable()
    val kind = enumerationByName<DraftKind>("kind", 16).default(DraftKind.IMPORT)
    val baseRevisionId =
        long("base_revision_id")
            .references(id, onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.NO_ACTION, fkName = "fk_revision_base")
            .nullable()
    val label = text("label").nullable()
    val derivationStale = bool("derivation_stale").default(false)
    val deriving = bool("deriving").default(false)
    val lastValidation = jsonb<String>("last_validation", { it }, { it }).nullable()
    val editorClaimBy = text("editor_claim_by").nullable()
    val editorClaimExpiresAt = timestamp("editor_claim_expires_at").nullable()
    val version = long("version").default(0)
    val createdBy = text("created_by").nullable()

    override val primaryKey = PrimaryKey(id, name = "gtfs_revision_pkey")

    init {
        index("ix_revision_feed_created", false, feedId, createdAt)
        // One ACTIVE revision per feed: a partial unique index.
        uniqueIndex("uq_gtfs_revision_active", feedId) { status eq GtfsRevisionStatus.ACTIVE }
    }

    override fun read(row: ResultRow) =
        GtfsRevision(
            feedId = row[feedId],
            status = row[status],
            sourceUrl = row[sourceUrl],
            contentSha256 = row[contentSha256],
            byteSize = row[byteSize],
            filesPresent = row[filesPresent] ?: emptyList(),
            rowCounts = row[rowCounts] ?: emptyMap(),
            feedStartDate = row[feedStartDate],
            feedEndDate = row[feedEndDate],
            validationReport = row[validationReport],
            errorMessage = row[errorMessage],
            createdAt = row[createdAt],
            activatedAt = row[activatedAt],
            supersededAt = row[supersededAt],
            kind = row[kind],
            baseRevisionId = row[baseRevisionId],
            label = row[label],
            derivationStale = row[derivationStale],
            deriving = row[deriving],
            lastValidation = row[lastValidation],
            editorClaimBy = row[editorClaimBy],
            editorClaimExpiresAt = row[editorClaimExpiresAt],
            version = row[version],
            createdBy = row[createdBy],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: GtfsRevision,
    ) {
        s[feedId] = row.feedId
        s[status] = row.status
        s[sourceUrl] = row.sourceUrl
        s[contentSha256] = row.contentSha256
        s[byteSize] = row.byteSize
        s[filesPresent] = row.filesPresent
        s[rowCounts] = row.rowCounts
        s[feedStartDate] = row.feedStartDate
        s[feedEndDate] = row.feedEndDate
        s[validationReport] = row.validationReport
        s[errorMessage] = row.errorMessage
        s[createdAt] = row.createdAt
        s[activatedAt] = row.activatedAt
        s[supersededAt] = row.supersededAt
        s[kind] = row.kind
        s[baseRevisionId] = row.baseRevisionId
        s[label] = row.label
        s[derivationStale] = row.derivationStale
        s[deriving] = row.deriving
        s[lastValidation] = row.lastValidation
        s[editorClaimBy] = row.editorClaimBy
        s[editorClaimExpiresAt] = row.editorClaimExpiresAt
        s[version] = row.version
        s[createdBy] = row.createdBy
    }
}
