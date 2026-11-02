package eu.transittrack.avl.match.cache

import java.time.LocalDate

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.schedule.read.ServiceDateResolver

/**
 * Revision-scoped, cached active-service-id sets for the AVL match path. [ServiceDateResolver] itself
 * stays uncached (its GraphQL callers pass unbounded client dates); the match path only asks about
 * dates at or near "today", so caching here is safe and bounded.
 */
@Component
class CachedServiceDateReader(
    private val resolver: ServiceDateResolver,
) {
    @Cacheable(AvlCaches.SERVICE_IDS, key = "#revisionId + ':' + #date")
    fun activeServiceIds(
        revisionId: Long,
        date: LocalDate,
    ): Set<String> = resolver.activeServiceIds(revisionId, date)
}
