package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.api.dto.GtfsFeedDto
import eu.transittrack.gtfs.api.dto.GtfsRevisionDto
import eu.transittrack.gtfs.api.dto.RegisterGtfsFeedInput
import eu.transittrack.gtfs.api.dto.UpdateGtfsFeedInput
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class GtfsMutationController(
    private val feedService: GtfsFeedService,
    private val ingestion: IngestionService,
    private val revisionService: RevisionService,
    private val revisions: GtfsRevisionRepository,
    private val feeds: GtfsFeedRepository,
    private val mapper: GtfsDtoMapper,
) {

    @MutationMapping
    fun registerGtfsFeed(@Argument input: RegisterGtfsFeedInput): GtfsFeedDto =
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
    fun updateGtfsFeed(@Argument code: String, @Argument input: UpdateGtfsFeedInput): GtfsFeedDto =
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
    fun deleteGtfsFeed(@Argument code: String): Boolean = feedService.delete(code)

    @MutationMapping
    fun ingestFeed(@Argument feedCode: String): GtfsRevisionDto =
        mapper.toDto(ingestion.ingest(feedCode), feedCode)

    @MutationMapping
    fun activateRevision(@Argument revisionId: String): GtfsRevisionDto {
        val revision = revisionService.activate(revisionId.toLong())
        val code = feeds.findById(revision.feedId).map { it.code }.orElse("")
        return mapper.toDto(revision, code)
    }

    @MutationMapping
    fun deleteRevision(@Argument revisionId: String): Boolean {
        val revision = revisions.findById(revisionId.toLong()).orElse(null) ?: return false
        check(revision.status != GtfsRevisionStatus.ACTIVE) { "cannot delete the ACTIVE revision" }
        revisionService.deleteWithRows(revision.id!!)
        return true
    }
}
