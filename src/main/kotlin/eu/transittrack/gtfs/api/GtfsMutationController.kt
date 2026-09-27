package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.FeedDto
import eu.transittrack.gtfs.api.dto.RegisterFeedInput
import eu.transittrack.gtfs.api.dto.RevisionDto
import eu.transittrack.gtfs.api.dto.UpdateFeedInput
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService

@Controller
class GtfsMutationController(
    private val feedService: GtfsFeedService,
    private val ingestion: IngestionService,
    private val revisionService: RevisionService,
    private val mapper: GtfsDtoMapper,
) {
    @MutationMapping
    fun registerFeed(
        @Argument input: RegisterFeedInput,
    ): FeedDto =
        mapper.toDto(
            feedService.register(
                FeedInput(
                    code = input.code,
                    name = input.name,
                    description = input.description,
                    url = input.url,
                    pollingCron = input.pollingCron,
                    enabled = input.enabled ?: true,
                    autoActivate = input.autoActivate,
                ),
            ),
        )

    @MutationMapping
    fun updateFeed(
        @Argument code: String,
        @Argument input: UpdateFeedInput,
    ): FeedDto =
        mapper.toDto(
            feedService.update(
                code,
                FeedInput(
                    code = code,
                    name = input.name,
                    description = input.description,
                    url = input.url,
                    pollingCron = input.pollingCron,
                    enabled = input.enabled ?: true,
                    autoActivate = input.autoActivate,
                ),
            ),
        )

    @MutationMapping
    fun deleteFeed(
        @Argument code: String,
    ): Boolean = feedService.delete(code)

    @MutationMapping
    fun ingestFeed(
        @Argument feedCode: String,
    ): RevisionDto = mapper.toDto(ingestion.ingest(feedCode), feedCode)

    @MutationMapping
    fun activateRevision(
        @Argument revisionId: String,
    ): RevisionDto {
        val revision = revisionService.activate(revisionId.toLong())
        val code = feedService.codeOf(revision.feedId) ?: ""
        return mapper.toDto(revision, code)
    }

    @MutationMapping
    fun deleteRevision(
        @Argument revisionId: String,
    ): Boolean {
        val revision = revisionService.findOrNull(revisionId.toLong()) ?: return false
        check(revision.status != GtfsRevisionStatus.ACTIVE) { "cannot delete the ACTIVE revision" }
        revisionService.deleteWithRows(revision.id!!)
        return true
    }
}
