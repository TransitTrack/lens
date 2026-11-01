package eu.transittrack.avl.match.cache

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.feed.GtfsFeedTable
import eu.transittrack.gtfs.revision.GtfsRevisionTable
import eu.transittrack.predict.learn.LearningKey
import eu.transittrack.schedule.model.TripPatternTable

/**
 * Revision-scoped, cached lookup of a trip pattern's [LearningKey] - its GTFS feed code and stable
 * `pattern_key` - so learned travel times are keyed by what survives a revision swap. A cache hit
 * runs no statement, and Exposed only takes a pooled connection for its first statement.
 */
@Component
@Transactional
class CachedLearningKeyReader {
    @Cacheable(AvlCaches.LEARNING_KEY, key = "#revisionId + ':' + #tripPatternId")
    fun keyOf(
        revisionId: Long,
        tripPatternId: Long,
    ): LearningKey? =
        TripPatternTable
            .join(GtfsRevisionTable, JoinType.INNER, onColumn = GtfsRevisionTable.id, otherColumn = TripPatternTable.revisionId)
            .join(GtfsFeedTable, JoinType.INNER, onColumn = GtfsFeedTable.id, otherColumn = GtfsRevisionTable.feedId)
            .select(GtfsFeedTable.code, TripPatternTable.patternKey)
            .where { TripPatternTable.id eq tripPatternId }
            .firstOrNull()
            ?.let { LearningKey(it[GtfsFeedTable.code], it[TripPatternTable.patternKey]) }
}
