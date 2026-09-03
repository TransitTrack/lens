package eu.transittrack.schedule.model

import org.springframework.data.jpa.repository.Query

import eu.transittrack.gtfs.model.RevisionScopedRepository

interface TravelTimesForStopPathRepository : RevisionScopedRepository<TravelTimesForStopPath, Long> {
    @Query(
        "select t from TravelTimesForStopPath t " +
            "where t.revisionId = :revisionId and t.tripPatternId = :tripPatternId order by t.stopPathIndex",
    )
    fun findByTripPatternOrdered(
        revisionId: Long,
        tripPatternId: Long,
    ): List<TravelTimesForStopPath>
}
