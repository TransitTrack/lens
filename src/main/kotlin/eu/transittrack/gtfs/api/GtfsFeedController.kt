package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.api.dto.GtfsFeedDto
import eu.transittrack.gtfs.api.dto.GtfsRevisionDto
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class GtfsFeedController(
    private val feedService: GtfsFeedService,
    private val revisions: GtfsRevisionRepository,
    private val mapper: GtfsDtoMapper,
) {

    @QueryMapping
    fun gtfsFeeds(): List<GtfsFeedDto> = feedService.list().map(mapper::toDto)

    @QueryMapping
    fun gtfsFeed(@Argument code: String): GtfsFeedDto? = feedService.get(code)?.let(mapper::toDto)

    @SchemaMapping(typeName = "GtfsFeed")
    fun revisions(feed: GtfsFeedDto): List<GtfsRevisionDto> {
        val feedId = feed.feedId ?: return emptyList()
        return revisions.findByFeedIdOrderByCreatedAtDesc(feedId).map { mapper.toDto(it, feed.code) }
    }

    @SchemaMapping(typeName = "GtfsFeed")
    fun activeRevision(feed: GtfsFeedDto): GtfsRevisionDto? {
        val feedId = feed.feedId ?: return null
        return revisions.findByFeedIdOrderByCreatedAtDesc(feedId)
            .firstOrNull { it.status == GtfsRevisionStatus.ACTIVE }
            ?.let { mapper.toDto(it, feed.code) }
    }
}
