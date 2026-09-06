package eu.transittrack.gtfs.revision

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

import eu.transittrack.gtfs.draft.DraftKind

@Repository
interface GtfsRevisionRepository : JpaRepository<GtfsRevision, Long> {
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
    @Modifying
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
}
