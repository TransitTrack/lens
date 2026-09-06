package eu.transittrack.gtfs.revision

import org.springframework.data.jpa.repository.JpaRepository
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
}
