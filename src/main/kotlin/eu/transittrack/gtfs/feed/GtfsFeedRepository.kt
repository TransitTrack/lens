package eu.transittrack.gtfs.feed

import org.springframework.data.jpa.repository.JpaRepository

interface GtfsFeedRepository : JpaRepository<GtfsFeed, Long> {
    fun findByCode(code: String): GtfsFeed?
    fun findAllByEnabledTrue(): List<GtfsFeed>
}
