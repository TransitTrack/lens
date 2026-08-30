package eu.transittrack.gtfs.revision

import org.springframework.data.jpa.repository.JpaRepository

interface GtfsRevisionRepository : JpaRepository<GtfsRevision, Long> {
    fun findByFeedIdAndStatus(feedId: Long, status: GtfsRevisionStatus): GtfsRevision?
    fun findByFeedIdOrderByCreatedAtDesc(feedId: Long): List<GtfsRevision>
    fun existsByFeedIdAndStatusIn(feedId: Long, statuses: Collection<GtfsRevisionStatus>): Boolean
}
