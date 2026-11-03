package eu.transittrack.gtfs.revision

import java.time.Instant
import java.time.LocalDate

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.json.jsonb

import eu.transittrack.ProjectJson
import eu.transittrack.db.RowTable
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.feed.GtfsFeedTable

class GtfsRevision(
    var feedId: Long,
    var status: GtfsRevisionStatus,
    var sourceUrl: String,
    var contentSha256: String? = null,
    var byteSize: Long? = null,
    var filesPresent: List<String> = emptyList(),
    var rowCounts: Map<String, Long> = emptyMap(),
    var feedStartDate: LocalDate? = null,
    var feedEndDate: LocalDate? = null,
    var validationReport: String? = null,
    var errorMessage: String? = null,
    var createdAt: Instant = Instant.now(),
    var activatedAt: Instant? = null,
    var supersededAt: Instant? = null,
    var kind: DraftKind = DraftKind.IMPORT,
    var baseRevisionId: Long? = null,
    var label: String? = null,
    var derivationStale: Boolean = false,
    var deriving: Boolean = false,
    var lastValidation: String? = null,
    var editorClaimBy: String? = null,
    var editorClaimExpiresAt: Instant? = null,
    var version: Long = 0,
    var createdBy: String? = null,
    var id: Long? = null,
)

object GtfsRevisionTable : RowTable<GtfsRevision>("gtfs_revision") {
    val id = long("id").autoIncrement(Sequence("gtfs_revision_seq", startWith = 1, incrementBy = 50))
    val feedId = long("feed_id")
        .references(GtfsFeedTable.id, onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION, fkName = "fk_revision_feed")
    val status = enumerationByName<GtfsRevisionStatus>("status", 16)
    val sourceUrl = text("source_url")
    val contentSha256 = varchar("content_sha256", 64).nullable()
    val byteSize = long("byte_size").nullable()
    val filesPresent =
        jsonb<List<String>>("files_present", ProjectJson).nullable()
    val rowCounts =
        jsonb<Map<String, Long>>("row_counts", ProjectJson).nullable()
    val feedStartDate = date("feed_start_date").nullable()
    val feedEndDate = date("feed_end_date").nullable()
    val validationReport = jsonb<String>("validation_report", { it }, { it }).nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestampTz("created_at")
    val activatedAt = timestampTz("activated_at").nullable()
    val supersededAt = timestampTz("superseded_at").nullable()
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
    val editorClaimExpiresAt = timestampTz("editor_claim_expires_at").nullable()
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
