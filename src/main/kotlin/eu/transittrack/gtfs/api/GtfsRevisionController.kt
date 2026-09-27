package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.RevisionDto
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService

@Controller
class GtfsRevisionController(
    private val revisionService: RevisionService,
    private val feedService: GtfsFeedService,
    private val mapper: GtfsDtoMapper,
) {
    @QueryMapping
    fun revisions(
        @Argument feedCode: String,
        @Argument status: GtfsRevisionStatus?,
    ): List<RevisionDto> {
        val feed = feedService.get(feedCode) ?: return emptyList()
        return revisionService
            .revisionsForFeed(feed.id!!)
            .filter {
                status == null || it.status == status
            }.map { mapper.toDto(it, feedCode) }
    }

    @QueryMapping
    fun revision(
        @Argument id: String,
    ): RevisionDto? {
        val revision = revisionService.findOrNull(id.toLong()) ?: return null
        val code = feedService.codeOf(revision.feedId) ?: ""
        return mapper.toDto(revision, code)
    }
}
