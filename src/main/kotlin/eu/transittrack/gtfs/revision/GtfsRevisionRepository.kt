package eu.transittrack.gtfs.revision

import java.time.Instant

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.draft.DraftKind

@Repository
@Transactional
class GtfsRevisionRepository {
    fun findById(id: Long): GtfsRevision? =
        GtfsRevisionTable
            .selectAll()
            .where { GtfsRevisionTable.id eq id }
            .singleOrNull()
            ?.let(GtfsRevisionTable::read)

    /**
     * Row-locking read for the draft-edit template: `DraftEditService.guard` takes a
     * `PESSIMISTIC_WRITE` lock on the draft row (`select ... for no key update`) so concurrent
     * `apply`/`undo`/`redo` on one draft serialize, turning the `version` check into a true
     * compare-and-swap (-> `StaleDraftException`). Deliberately not an optimistic version column on
     * the table's writers: that would impose optimistic locking on every other writer of
     * `gtfs_revision` (ingestion, revision lifecycle, AVL).
     */
    fun findByIdForUpdate(id: Long): GtfsRevision? =
        GtfsRevisionTable
            .selectAll()
            .where { GtfsRevisionTable.id eq id }
            .forUpdate(ForUpdateOption.PostgreSQL.ForNoKeyUpdate)
            .singleOrNull()
            ?.let(GtfsRevisionTable::read)

    fun findByFeedAndStatus(
        feedId: Long,
        status: GtfsRevisionStatus,
    ): GtfsRevision? =
        GtfsRevisionTable
            .selectAll()
            .where { (GtfsRevisionTable.feedId eq feedId) and (GtfsRevisionTable.status eq status) }
            .singleOrNull()
            ?.let(GtfsRevisionTable::read)

    fun findByFeedNewestFirst(feedId: Long): List<GtfsRevision> =
        GtfsRevisionTable
            .selectAll()
            .where { GtfsRevisionTable.feedId eq feedId }
            .orderBy(GtfsRevisionTable.createdAt, SortOrder.DESC)
            .map(GtfsRevisionTable::read)

    fun existsByFeedAndStatusIn(
        feedId: Long,
        statuses: Collection<GtfsRevisionStatus>,
    ): Boolean =
        !GtfsRevisionTable
            .selectAll()
            .where { (GtfsRevisionTable.feedId eq feedId) and (GtfsRevisionTable.status inList statuses) }
            .limit(1)
            .empty()

    fun findByStatus(status: GtfsRevisionStatus): List<GtfsRevision> =
        GtfsRevisionTable
            .selectAll()
            .where { GtfsRevisionTable.status eq status }
            .map(GtfsRevisionTable::read)

    fun findByKindAndStatus(
        kind: DraftKind,
        status: GtfsRevisionStatus,
    ): List<GtfsRevision> =
        GtfsRevisionTable
            .selectAll()
            .where { (GtfsRevisionTable.kind eq kind) and (GtfsRevisionTable.status eq status) }
            .map(GtfsRevisionTable::read)

    /** Revision ids that some DRAFT revision was forked from — prune must not delete these. */
    fun findBaseRevisionIdsReferencedByDrafts(): List<Long> =
        GtfsRevisionTable
            .select(GtfsRevisionTable.baseRevisionId)
            .where { (GtfsRevisionTable.kind eq DraftKind.DRAFT) and GtfsRevisionTable.baseRevisionId.isNotNull() }
            .withDistinct()
            .mapNotNull { it[GtfsRevisionTable.baseRevisionId] }

    /** Inserts when [row] has no id, else updates it in place; returns [row] with its id set. */
    fun save(row: GtfsRevision): GtfsRevision {
        val id = row.id
        if (id == null) {
            row.id = GtfsRevisionTable.insert { GtfsRevisionTable.bind(it, row) }[GtfsRevisionTable.id]
        } else {
            GtfsRevisionTable.update({ GtfsRevisionTable.id eq id }) { GtfsRevisionTable.bind(it, row) }
        }
        return row
    }

    fun count(): Long = GtfsRevisionTable.selectAll().count()

    fun delete(row: GtfsRevision) {
        val id = row.id ?: return
        deleteById(id)
    }

    fun deleteById(id: Long) {
        GtfsRevisionTable.deleteWhere { GtfsRevisionTable.id eq id }
    }

    /**
     * Atomically claim the editor lock: sets the holder only when the lock is free, expired, already
     * held by [who], or [takeOver] is set. Returns the affected-row count (1 = claimed, 0 = locked).
     */
    fun tryClaimEditor(
        id: Long,
        who: String,
        exp: Instant,
        now: Instant,
        takeOver: Boolean,
    ): Int {
        val claimable: Op<Boolean> =
            if (takeOver) {
                Op.TRUE
            } else {
                GtfsRevisionTable.editorClaimBy.isNull() or
                    (GtfsRevisionTable.editorClaimBy eq who) or
                    (GtfsRevisionTable.editorClaimExpiresAt less now)
            }
        return GtfsRevisionTable.update({ (GtfsRevisionTable.id eq id) and claimable }) {
            it[editorClaimBy] = who
            it[editorClaimExpiresAt] = exp
        }
    }

    /**
     * Atomically claim the rebuild slot: flips `deriving` false -> true for [id] only when it is
     * currently free. Returns 1 when claimed, 0 when a rebuild is already in flight. Replaces the
     * former read-check-then-save, which was a TOCTOU across separate transactions.
     */
    fun tryClaimRebuild(id: Long): Int =
        GtfsRevisionTable.update({ (GtfsRevisionTable.id eq id) and (GtfsRevisionTable.deriving eq false) }) {
            it[deriving] = true
        }

    /**
     * Clears any `deriving = true` left over from a rebuild that never completed (JVM killed
     * mid-run). Safe to call only at startup, when the in-memory job registry guarantees no rebuild
     * is legitimately in flight. Returns the number of rows cleared.
     */
    fun clearDanglingDeriving(): Int =
        GtfsRevisionTable.update({ GtfsRevisionTable.deriving eq true }) {
            it[deriving] = false
        }
}
