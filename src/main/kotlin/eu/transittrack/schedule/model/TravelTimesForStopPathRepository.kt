package eu.transittrack.schedule.model

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository

import eu.transittrack.db.bridge.ExposedBridge
import eu.transittrack.gtfs.model.RevisionScopedRepository

@Repository
@ExposedBridge
class TravelTimesForStopPathRepository : RevisionScopedRepository<TravelTimesForStopPath>(TravelTimesForStopPathTable) {
    fun findByTripPatternOrdered(
        revisionId: Long,
        tripPatternId: Long,
    ): List<TravelTimesForStopPath> =
        TravelTimesForStopPathTable
            .selectAll()
            .where {
                (TravelTimesForStopPathTable.revisionId eq revisionId) and
                    (TravelTimesForStopPathTable.tripPatternId eq tripPatternId)
            }.orderBy(TravelTimesForStopPathTable.stopPathIndex)
            .map(TravelTimesForStopPathTable::read)
}
