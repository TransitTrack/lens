package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.FeedDto
import eu.transittrack.gtfs.api.dto.RevisionDto
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

@Controller
class GtfsFeedController(
    private val feedService: GtfsFeedService,
    private val revisions: GtfsRevisionRepository,
    private val mapper: GtfsDtoMapper,
) {
    @QueryMapping fun feeds(): List<FeedDto> = feedService.list().map(mapper::toDto)

    @QueryMapping
    fun feed(
        @Argument code: String,
    ): FeedDto? = feedService.get(code)?.let(mapper::toDto)

    @SchemaMapping(typeName = "Feed")
    fun revisions(feed: FeedDto): List<RevisionDto> {
        val feedId = feed.feedId ?: return emptyList()
        return revisions.findByFeedNewestFirst(feedId).map { mapper.toDto(it, feed.code) }
    }

    @SchemaMapping(typeName = "Feed")
    fun activeRevision(feed: FeedDto): RevisionDto? {
        val feedId = feed.feedId ?: return null
        return revisions
            .findByFeedNewestFirst(feedId)
            .firstOrNull { it.status == GtfsRevisionStatus.ACTIVE }
            ?.let { mapper.toDto(it, feed.code) }
    }
}
