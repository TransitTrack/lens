package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

import eu.transittrack.predict.learn.LearningKey

/**
 * Revision-scoped, cached lookup of a trip pattern's [LearningKey] - its GTFS feed code and stable
 * `pattern_key` - so learned travel times are keyed by what survives a revision swap.
 */
@Component
class CachedLearningKeyReader(
    private val jdbc: JdbcTemplate,
) {
    @Cacheable(AvlCaches.LEARNING_KEY, key = "#revisionId + ':' + #tripPatternId")
    fun keyOf(
        revisionId: Long,
        tripPatternId: Long,
    ): LearningKey? =
        jdbc
            .query(
                "select f.code, p.pattern_key from trip_patterns p " +
                    "join gtfs_revision r on r.id = p.revision_id join gtfs_feed f on f.id = r.feed_id where p.id = ?",
                { rs, _ -> LearningKey(rs.getString(1), rs.getString(2)) },
                tripPatternId,
            ).firstOrNull()
}
