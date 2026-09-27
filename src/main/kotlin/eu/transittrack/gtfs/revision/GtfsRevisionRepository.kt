package eu.transittrack.gtfs.revision

import jakarta.persistence.LockModeType

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.draft.DraftKind

@Repository
interface GtfsRevisionRepository : JpaRepository<GtfsRevision, Long> {
    /**
     * Row-locking read for the draft-edit template: `DraftEditService.guard` takes a
     * `PESSIMISTIC_WRITE` lock on the draft row so concurrent `apply`/`undo`/`redo` on one draft
     * serialize, turning the `version` check into a true compare-and-swap (→ `StaleDraftException`).
     * Deliberately not `@Version` on the entity — that would impose optimistic locking on every
     * other writer of `gtfs_revision` (ingestion, revision lifecycle, AVL).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GtfsRevision r where r.id = :id")
    fun findByIdForUpdate(id: Long): GtfsRevision?

    @Query("select r from GtfsRevision r where r.feedId = :feedId and r.status = :status")
    fun findByFeedAndStatus(
        feedId: Long,
        status: GtfsRevisionStatus,
    ): GtfsRevision?

    @Query("select r from GtfsRevision r where r.feedId = :feedId order by r.createdAt desc")
    fun findByFeedNewestFirst(feedId: Long): List<GtfsRevision>

    @Query(
        "select case when count(r) > 0 then true else false end " +
            "from GtfsRevision r where r.feedId = :feedId and r.status in :statuses",
    )
    fun existsByFeedAndStatusIn(
        feedId: Long,
        statuses: Collection<GtfsRevisionStatus>,
    ): Boolean

    @Query("select r from GtfsRevision r where r.status = :status")
    fun findByStatus(status: GtfsRevisionStatus): List<GtfsRevision>

    @Query("select r from GtfsRevision r where r.kind = :kind and r.status = :status")
    fun findByKindAndStatus(
        kind: DraftKind,
        status: GtfsRevisionStatus,
    ): List<GtfsRevision>

    /** Revision ids that some DRAFT revision was forked from — prune must not delete these. */
    @Query(
        "select distinct r.baseRevisionId from GtfsRevision r " +
            "where r.kind = eu.transittrack.gtfs.draft.DraftKind.DRAFT and r.baseRevisionId is not null",
    )
    fun findBaseRevisionIdsReferencedByDrafts(): List<Long>

    /**
     * Atomically claim the editor lock: sets the holder only when the lock is free, expired, already
     * held by [who], or [takeOver] is set. Returns the affected-row count (1 = claimed, 0 = locked).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value =
            "update gtfs_revision set editor_claim_by = :who, editor_claim_expires_at = :exp " +
                "where id = :id and (:takeOver = true or editor_claim_by is null " +
                "or editor_claim_by = :who or editor_claim_expires_at < :now)",
        nativeQuery = true,
    )
    fun tryClaimEditor(
        id: Long,
        who: String,
        exp: java.time.Instant,
        now: java.time.Instant,
        takeOver: Boolean,
    ): Int

    /**
     * Atomically claim the rebuild slot: flips `deriving` false -> true for [id] only when it is
     * currently free. Returns 1 when claimed, 0 when a rebuild is already in flight. Replaces the
     * former read-check-then-save, which was a TOCTOU across separate transactions.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update GtfsRevision r set r.deriving = true where r.id = :id and r.deriving = false")
    fun tryClaimRebuild(id: Long): Int

    /**
     * Clears any `deriving = true` left over from a rebuild that never completed (JVM killed
     * mid-run). Safe to call only at startup, when the in-memory job registry guarantees no rebuild
     * is legitimately in flight. Returns the number of rows cleared.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update GtfsRevision r set r.deriving = false where r.deriving = true")
    fun clearDanglingDeriving(): Int
}
