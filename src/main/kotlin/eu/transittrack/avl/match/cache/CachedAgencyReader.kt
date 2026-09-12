package eu.transittrack.avl.match.cache

import java.time.ZoneId

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.gtfs.model.AgencyRepository

/** Revision-scoped, cached agency timezone lookup for the AVL match path. */
@Component
class CachedAgencyReader(
    private val agencies: AgencyRepository,
) {
    @Cacheable(AvlCaches.AGENCY_TIMEZONE, key = "#revisionId")
    fun timezoneOf(revisionId: Long): ZoneId? = agencies.findByRevisionId(revisionId).firstNotNullOfOrNull { it.agencyTimezone }
}
