package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.api.dto.GtfsFeedDto
import eu.transittrack.gtfs.api.dto.GtfsRevisionDto
import eu.transittrack.gtfs.api.dto.GtfsValidationSummaryDto
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.validate.ValidationReport
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Maps GTFS domain entities to their GraphQL DTOs.
 *
 * The revision's `validationReport` is a raw JSON string; deserializing it into
 * [ValidationReport] (a `data class` with no no-arg constructor) requires the
 * Spring-configured [JsonMapper] bean, which Boot 4 wires with `jackson-module-kotlin`.
 */
@Component
class GtfsDtoMapper(private val jsonMapper: JsonMapper) {

    fun toDto(feed: GtfsFeed): GtfsFeedDto = GtfsFeedDto(
        code = feed.code,
        name = feed.name,
        description = feed.description,
        url = feed.url,
        pollingCron = feed.pollingCron,
        enabled = feed.enabled,
        source = feed.source.name,
        feedId = feed.id,
    )

    fun toDto(revision: GtfsRevision, feedCode: String): GtfsRevisionDto {
        val summary = revision.validationReport?.let { json ->
            val report = jsonMapper.readValue(json, ValidationReport::class.java)
            GtfsValidationSummaryDto(report.errorCount, report.warningCount)
        }
        return GtfsRevisionDto(
            id = revision.id.toString(),
            feedCode = feedCode,
            status = revision.status,
            createdAt = revision.createdAt.toString(),
            activatedAt = revision.activatedAt?.toString(),
            supersededAt = revision.supersededAt?.toString(),
            contentSha256 = revision.contentSha256,
            byteSize = revision.byteSize,
            filesPresent = revision.filesPresent,
            rowCounts = revision.rowCounts,
            feedStartDate = revision.feedStartDate?.toString(),
            feedEndDate = revision.feedEndDate?.toString(),
            validationSummary = summary,
            errorMessage = revision.errorMessage,
        )
    }
}
