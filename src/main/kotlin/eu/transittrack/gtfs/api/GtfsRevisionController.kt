package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.api.dto.GtfsRevisionDto
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class GtfsRevisionController(
    private val revisions: GtfsRevisionRepository,
    private val feeds: GtfsFeedRepository,
    private val mapper: GtfsDtoMapper,
) {

    @QueryMapping
    fun gtfsRevisions(
        @Argument feedCode: String,
        @Argument status: GtfsRevisionStatus?,
    ): List<GtfsRevisionDto> {
        val feed = feeds.findByCode(feedCode) ?: return emptyList()
        return revisions.findByFeedIdOrderByCreatedAtDesc(feed.id!!)
            .filter { status == null || it.status == status }
            .map { mapper.toDto(it, feedCode) }
    }

    @QueryMapping
    fun gtfsRevision(@Argument id: String): GtfsRevisionDto? {
        val revision = revisions.findById(id.toLong()).orElse(null) ?: return null
        val code = feeds.findById(revision.feedId).map { it.code }.orElse("")
        return mapper.toDto(revision, code)
    }
}
