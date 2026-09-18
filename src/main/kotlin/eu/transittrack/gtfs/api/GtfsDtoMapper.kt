package eu.transittrack.gtfs.api

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.api.dto.FeedDto
import eu.transittrack.gtfs.api.dto.RevisionDto
import eu.transittrack.gtfs.api.dto.ValidationNoticeDto
import eu.transittrack.gtfs.api.dto.ValidationSummaryDto
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.validate.LoadValidationReport

/**
 * Maps GTFS domain entities to their GraphQL DTOs.
 *
 * The revision's `validationReport` is a raw JSON string; deserializing it into
 * [LoadValidationReport] (a `data class` with no no-arg constructor) requires the Spring-configured
 * [JsonMapper] bean, which Boot 4 wires with `jackson-module-kotlin`.
 */
@Component
class GtfsDtoMapper(
    private val jsonMapper: JsonMapper,
) {
    fun toDto(feed: GtfsFeed): FeedDto =
        FeedDto(
            code = feed.code,
            name = feed.name,
            description = feed.description,
            url = feed.url,
            pollingCron = feed.pollingCron,
            enabled = feed.enabled,
            source = feed.source.name,
            feedId = feed.id,
        )

    fun toDto(
        revision: GtfsRevision,
        feedCode: String,
    ): RevisionDto {
        val summary =
            revision.validationReport?.let { json ->
                val report = jsonMapper.readValue(json, LoadValidationReport::class.java)
                ValidationSummaryDto(
                    errorCount = report.errorCount,
                    warningCount = report.warningCount,
                    notices =
                        report.issues.map {
                            ValidationNoticeDto(
                                rule = it.rule,
                                severity = it.severity.name,
                                count = it.count,
                                sample = it.sample,
                            )
                        },
                )
            }
        return RevisionDto(
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
