package eu.transittrack.gtfs.draft

import java.time.Instant

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.jsonb
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.db.RowTable
import eu.transittrack.gtfs.revision.GtfsRevisionTable

class DraftEdit(
    var revisionId: Long,
    var seq: Int,
    var op: String,
    var summary: String,
    var forward: String,
    var inverse: String,
    var appliedAt: Instant,
    var undone: Boolean = false,
) {
    var id: Long? = null
}

object DraftEditTable : RowTable<DraftEdit>("draft_edit") {
    val id = long("id").autoIncrement(Sequence("draft_edit_seq", startWith = 1, incrementBy = 50))
    val revisionId =
        long("revision_id").references(
            GtfsRevisionTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_draft_edit_revision",
        )
    val seq = integer("seq")
    val op = text("op")
    val summary = text("summary")
    val forward = jsonb<String>("forward", { it }, { it })
    val inverse = jsonb<String>("inverse", { it }, { it })
    val appliedAt = timestampTz("applied_at")
    val undone = bool("undone").default(false)

    override val primaryKey = PrimaryKey(id, name = "draft_edit_pkey")

    init {
        uniqueIndex("uq_draft_edit_revision_seq", revisionId, seq)
    }

    override fun read(row: ResultRow) =
        DraftEdit(
            revisionId = row[revisionId],
            seq = row[seq],
            op = row[op],
            summary = row[summary],
            forward = row[forward],
            inverse = row[inverse],
            appliedAt = row[appliedAt],
            undone = row[undone],
        ).also { it.id = row[id] }

    override fun bind(
        s: UpdateBuilder<*>,
        row: DraftEdit,
    ) {
        s[revisionId] = row.revisionId
        s[seq] = row.seq
        s[op] = row.op
        s[summary] = row.summary
        s[forward] = row.forward
        s[inverse] = row.inverse
        s[appliedAt] = row.appliedAt
        s[undone] = row.undone
    }
}

@Repository
@Transactional
class DraftEditRepository {
    fun findByRevision(revisionId: Long): List<DraftEdit> =
        DraftEditTable
            .selectAll()
            .where { DraftEditTable.revisionId eq revisionId }
            .orderBy(DraftEditTable.seq, SortOrder.ASC)
            .map(DraftEditTable::read)

    /** The most recently applied (not undone) edit for a revision — the one `undo` would revert. */
    fun findLastApplied(revisionId: Long): DraftEdit? =
        DraftEditTable
            .selectAll()
            .where { (DraftEditTable.revisionId eq revisionId) and (DraftEditTable.undone eq false) }
            .orderBy(DraftEditTable.seq, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(DraftEditTable::read)

    /** The earliest undone edit for a revision — the one `redo` would reapply. */
    fun findNextRedo(revisionId: Long): DraftEdit? =
        DraftEditTable
            .selectAll()
            .where { (DraftEditTable.revisionId eq revisionId) and (DraftEditTable.undone eq true) }
            .orderBy(DraftEditTable.seq, SortOrder.ASC)
            .limit(1)
            .singleOrNull()
            ?.let(DraftEditTable::read)

    fun countByRevision(revisionId: Long): Long =
        DraftEditTable
            .selectAll()
            .where { DraftEditTable.revisionId eq revisionId }
            .count()

    fun deleteUndone(revisionId: Long): Int =
        DraftEditTable.deleteWhere { (DraftEditTable.revisionId eq revisionId) and (DraftEditTable.undone eq true) }

    fun deleteForRevision(revisionId: Long): Int = DraftEditTable.deleteWhere { DraftEditTable.revisionId eq revisionId }

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: DraftEdit): DraftEdit {
        val id = row.id
        if (id == null) {
            row.id = DraftEditTable.insert { DraftEditTable.bind(it, row) }[DraftEditTable.id]
        } else {
            DraftEditTable.update({ DraftEditTable.id eq id }) { DraftEditTable.bind(it, row) }
        }
        return row
    }
}
