package eu.transittrack.gtfs.api

import io.github.transittrack.gtfsanalyzer.Notice
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.validate.EntityFindingCount
import eu.transittrack.gtfs.validate.LoadValidationReport
import eu.transittrack.gtfs.validate.ValidationFinding
import eu.transittrack.gtfs.validate.countsByEntity
import eu.transittrack.gtfs.validate.toFindings

/**
 * Per-entity GTFS analyzer findings, read from the revision's stored validation report. Empty for
 * revisions that were never validated or whose report predates the analyzer (format 2).
 */
@Controller
@ConditionalOnRole(Role.API)
class GtfsFindingsController(
    private val resolver: RevisionResolver,
    private val revisions: RevisionService,
) {
    @QueryMapping
    fun validationFindings(
        @Argument feedCode: String,
        @Argument entityType: String?,
        @Argument entityId: String?,
        @Argument revisionId: String?,
    ): List<ValidationFinding> = noticesOf(feedCode, revisionId).toFindings(entityType, entityId)

    @QueryMapping
    fun validationFindingCounts(
        @Argument feedCode: String,
        @Argument entityType: String,
        @Argument revisionId: String?,
    ): List<EntityFindingCount> = noticesOf(feedCode, revisionId).countsByEntity(entityType)

    private fun noticesOf(
        feedCode: String,
        revisionId: String?,
    ): List<Notice> {
        val json = revisions.revision(resolver.resolve(feedCode, revisionId)).validationReport ?: return emptyList()
        return LoadValidationReport
            .fromJson(json)
            ?.result
            ?.notices
            .orEmpty()
    }
}
