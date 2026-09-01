package eu.transittrack.gtfs.feed

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GtfsFeedRepository : JpaRepository<GtfsFeed, Long> {
    @Query("select f from GtfsFeed f where f.code = :code")
    fun findByCode(code: String): GtfsFeed?

    @Query("select f from GtfsFeed f where f.enabled = true")
    fun findAllEnabled(): List<GtfsFeed>
}
