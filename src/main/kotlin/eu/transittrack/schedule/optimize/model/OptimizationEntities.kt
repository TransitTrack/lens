package eu.transittrack.schedule.optimize.model

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
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.jsonb
import org.springframework.stereotype.Repository

import eu.transittrack.db.RowTable
import eu.transittrack.db.bridge.ExposedBridge

enum class OptimizationRunState { QUEUED, RUNNING, SUCCEEDED, FAILED }

enum class OptimizationRecommendationKind { STOP_TIME, TRIP_SHIFT }

enum class OptimizationRecommendationStatus { PENDING, SELECTED, APPLIED, DISMISSED, UNAVAILABLE }

/**
 * One asynchronous analysis request. `feedCode`/`revisionId` freeze the feed's ACTIVE revision at
 * submission time — see [eu.transittrack.schedule.optimize.ScheduleOptimizationService]; a run is
 * never translated to a later revision even if one is activated while it is `QUEUED`/`RUNNING`.
 * `feedCode` is `gtfs_feed.code` — unrelated to any `avl_feed`. Filter fields are the full
 * normalized request inputs (all optional besides the feed/observed window/minimum-sample count,
 * enforced by [eu.transittrack.schedule.optimize.OptimizationRunRequest]).
 */
@Entity
@Table(name = "schedule_optimization_run")
class OptimizationRunRow(
    @Column(name = "feed_code", nullable = false) var feedCode: String,
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "service_id") var serviceId: String? = null,
    @Column(name = "route_id") var routeId: String? = null,
    @Column(name = "direction_id") var directionId: Int? = null,
    @Column(name = "window_from_sec") var windowFromSec: Int? = null,
    @Column(name = "window_to_sec") var windowToSec: Int? = null,
    @Column(name = "observed_from", nullable = false) var observedFrom: Instant,
    @Column(name = "observed_to", nullable = false) var observedTo: Instant,
    @Column(name = "minimum_samples", nullable = false) var minimumSamples: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var state: OptimizationRunState = OptimizationRunState.QUEUED,
    @Column(name = "started_at") var startedAt: Instant? = null,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Column var error: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scheduleOptimizationRunSeq")
    @SequenceGenerator(name = "scheduleOptimizationRunSeq", sequenceName = "schedule_optimization_run_seq", allocationSize = 50)
    var id: Long? = null,
)

object OptimizationRunTable : RowTable<OptimizationRunRow>("schedule_optimization_run") {
    val id = long("id").autoIncrement(Sequence("schedule_optimization_run_seq", startWith = 1, incrementBy = 50))
    val feedCode = varchar("feed_code", 64)
    val revisionId = long("revision_id")
    val serviceId = text("service_id").nullable()
    val routeId = text("route_id").nullable()
    val directionId = integer("direction_id").nullable()
    val windowFromSec = integer("window_from_sec").nullable()
    val windowToSec = integer("window_to_sec").nullable()
    val observedFrom = timestampTz("observed_from")
    val observedTo = timestampTz("observed_to")
    val minimumSamples = integer("minimum_samples")
    val state = enumerationByName<OptimizationRunState>("state", 16)
    val startedAt = timestampTz("started_at").nullable()
    val completedAt = timestampTz("completed_at").nullable()
    val error = text("error").nullable()
    val createdAt = timestampTz("created_at")

    override val primaryKey = PrimaryKey(id, name = "schedule_optimization_run_pkey")

    override fun read(row: ResultRow) =
        OptimizationRunRow(
            feedCode = row[feedCode],
            revisionId = row[revisionId],
            serviceId = row[serviceId],
            routeId = row[routeId],
            directionId = row[directionId],
            windowFromSec = row[windowFromSec],
            windowToSec = row[windowToSec],
            observedFrom = row[observedFrom],
            observedTo = row[observedTo],
            minimumSamples = row[minimumSamples],
            state = row[state],
            startedAt = row[startedAt],
            completedAt = row[completedAt],
            error = row[error],
            createdAt = row[createdAt],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: OptimizationRunRow,
    ) {
        s[feedCode] = row.feedCode
        s[revisionId] = row.revisionId
        s[serviceId] = row.serviceId
        s[routeId] = row.routeId
        s[directionId] = row.directionId
        s[windowFromSec] = row.windowFromSec
        s[windowToSec] = row.windowToSec
        s[observedFrom] = row.observedFrom
        s[observedTo] = row.observedTo
        s[minimumSamples] = row.minimumSamples
        s[state] = row.state
        s[startedAt] = row.startedAt
        s[completedAt] = row.completedAt
        s[error] = row.error
        s[createdAt] = row.createdAt
    }
}

@Repository
@ExposedBridge
class OptimizationRunRepository {
    fun findById(id: Long): OptimizationRunRow? =
        OptimizationRunTable
            .selectAll()
            .where { OptimizationRunTable.id eq id }
            .singleOrNull()
            ?.let(OptimizationRunTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: OptimizationRunRow): OptimizationRunRow {
        val id = row.id
        if (id == null) {
            row.id = OptimizationRunTable.insert { OptimizationRunTable.bind(it, row) }[OptimizationRunTable.id]
        } else {
            OptimizationRunTable.update({ OptimizationRunTable.id eq id }) { OptimizationRunTable.bind(it, row) }
        }
        return row
    }

    /**
     * Bounded, newest-first page of runs for one feed — backs the UI's runs-history list.
     * An arbitrary `offset`/`limit` pair maps directly to `OFFSET`/`LIMIT`.
     */
    fun page(
        feedCode: String,
        offset: Int,
        limit: Int,
    ): List<OptimizationRunRow> =
        OptimizationRunTable
            .selectAll()
            .where { OptimizationRunTable.feedCode eq feedCode }
            .orderBy(OptimizationRunTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map(OptimizationRunTable::read)

    /** Deletes runs created before [cutoff]; their recommendations cascade via the foreign key. */
    fun deleteCreatedBefore(cutoff: Instant): Int = OptimizationRunTable.deleteWhere { createdAt less cutoff }
}

/**
 * A persistent, reviewable proposed edit generated by exactly one run. `currentValue`,
 * `proposedValue`, and `evidence` are JSONB (see [org.hibernate.annotations.JdbcTypeCode] usage
 * elsewhere in this codebase, e.g. `eu.transittrack.gtfs.draft.DraftEdit`) so future analysis
 * versions can enrich explanations without a migration; `sampleCount`/`deltaSec` stay strongly
 * typed columns because they are queried/aggregated directly. `conflictKey` identifies edits that
 * cannot safely coexist at apply time (Task 5).
 */
@Entity
@Table(name = "schedule_optimization_recommendation")
class OptimizationRecommendationRow(
    @Column(name = "run_id", nullable = false) var runId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var kind: OptimizationRecommendationKind,
    @Column(name = "sample_count", nullable = false) var sampleCount: Int,
    @Column(name = "delta_sec", nullable = false) var deltaSec: Int,
    @Column(nullable = false) var reason: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    var status: OptimizationRecommendationStatus = OptimizationRecommendationStatus.PENDING,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "current_value") var currentValue: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "proposed_value") var proposedValue: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) @Column var evidence: String? = null,
    @Column(name = "conflict_key") var conflictKey: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "scheduleOptimizationRecommendationSeq")
    @SequenceGenerator(
        name = "scheduleOptimizationRecommendationSeq",
        sequenceName = "schedule_optimization_recommendation_seq",
        allocationSize = 50,
    )
    var id: Long? = null,
)

object OptimizationRecommendationTable : RowTable<OptimizationRecommendationRow>("schedule_optimization_recommendation") {
    val id = long("id").autoIncrement(Sequence("schedule_optimization_recommendation_seq", startWith = 1, incrementBy = 50))
    val runId =
        long("run_id").references(
            OptimizationRunTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_optimization_recommendation_run",
        )
    val kind = enumerationByName<OptimizationRecommendationKind>("kind", 16)
    val sampleCount = integer("sample_count")
    val deltaSec = integer("delta_sec")
    val reason = text("reason")
    val status = enumerationByName<OptimizationRecommendationStatus>("status", 16).default(OptimizationRecommendationStatus.PENDING)
    val currentValue = jsonb<String>("current_value", { it }, { it }).nullable()
    val proposedValue = jsonb<String>("proposed_value", { it }, { it }).nullable()
    val evidence = jsonb<String>("evidence", { it }, { it }).nullable()
    val conflictKey = text("conflict_key").nullable()

    override val primaryKey = PrimaryKey(id, name = "schedule_optimization_recommendation_pkey")

    init {
        index("ix_optimization_recommendation_run", false, runId, id)
        index("ix_optimization_recommendation_run_status", false, runId, status, id)
    }

    override fun read(row: ResultRow) =
        OptimizationRecommendationRow(
            runId = row[runId],
            kind = row[kind],
            sampleCount = row[sampleCount],
            deltaSec = row[deltaSec],
            reason = row[reason],
            status = row[status],
            currentValue = row[currentValue],
            proposedValue = row[proposedValue],
            evidence = row[evidence],
            conflictKey = row[conflictKey],
            id = row[id],
        )

    override fun bind(
        s: UpdateBuilder<*>,
        row: OptimizationRecommendationRow,
    ) {
        s[runId] = row.runId
        s[kind] = row.kind
        s[sampleCount] = row.sampleCount
        s[deltaSec] = row.deltaSec
        s[reason] = row.reason
        s[status] = row.status
        s[currentValue] = row.currentValue
        s[proposedValue] = row.proposedValue
        s[evidence] = row.evidence
        s[conflictKey] = row.conflictKey
    }
}

@Repository
@ExposedBridge
class OptimizationRecommendationRepository {
    fun findAllById(ids: Iterable<Long>): List<OptimizationRecommendationRow> =
        OptimizationRecommendationTable
            .selectAll()
            .where { OptimizationRecommendationTable.id inList ids.toList() }
            .map(OptimizationRecommendationTable::read)

    fun findById(id: Long): OptimizationRecommendationRow? =
        OptimizationRecommendationTable
            .selectAll()
            .where { OptimizationRecommendationTable.id eq id }
            .singleOrNull()
            ?.let(OptimizationRecommendationTable::read)

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: OptimizationRecommendationRow): OptimizationRecommendationRow {
        val id = row.id
        if (id == null) {
            row.id =
                OptimizationRecommendationTable.insert { OptimizationRecommendationTable.bind(it, row) }[OptimizationRecommendationTable.id]
        } else {
            OptimizationRecommendationTable.update({ OptimizationRecommendationTable.id eq id }) {
                OptimizationRecommendationTable.bind(it, row)
            }
        }
        return row
    }

    fun saveAll(rows: Iterable<OptimizationRecommendationRow>): List<OptimizationRecommendationRow> = rows.map(::save)

    /**
     * Row-locking read for `ScheduleOptimizationService.apply`: a `for no key update` lock on every
     * selected recommendation row so two concurrent `apply` calls whose recommendation-id sets
     * overlap serialize on the shared rows, turning the `PENDING`-status check that follows into a
     * true compare-and-swap rather than a TOCTOU race that could fork two drafts from one selection.
     * Disjoint selections (no shared row) still proceed concurrently — this locks the individual
     * recommendation rows, not the whole run. Modeled on
     * [eu.transittrack.gtfs.revision.GtfsRevisionRepository.findByIdForUpdate].
     */
    fun findAllByIdForUpdate(ids: Collection<Long>): List<OptimizationRecommendationRow> =
        OptimizationRecommendationTable
            .selectAll()
            .where { OptimizationRecommendationTable.id inList ids.toList() }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .map(OptimizationRecommendationTable::read)

    /**
     * Stable `(run_id, status, id)` pagination for `ScheduleOptimizationService.listRecommendations`.
     * An arbitrary `offset`/`limit` pair maps directly to `OFFSET`/`LIMIT` instead of being
     * constrained to page-aligned windows. A null [status] returns every status.
     */
    fun page(
        runId: Long,
        status: String?,
        offset: Int,
        limit: Int,
    ): List<OptimizationRecommendationRow> {
        val byStatus: Op<Boolean> =
            if (status == null) Op.TRUE else OptimizationRecommendationTable.status eq OptimizationRecommendationStatus.valueOf(status)
        return OptimizationRecommendationTable
            .selectAll()
            .where { (OptimizationRecommendationTable.runId eq runId) and byStatus }
            .orderBy(OptimizationRecommendationTable.status to SortOrder.ASC, OptimizationRecommendationTable.id to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map(OptimizationRecommendationTable::read)
    }
}
