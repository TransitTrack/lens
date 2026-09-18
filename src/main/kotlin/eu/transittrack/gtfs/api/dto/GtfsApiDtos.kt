package eu.transittrack.gtfs.api.dto

/** GraphQL projection of `eu.transittrack.gtfs.feed.GtfsFeed`. */
data class FeedDto(
    val code: String,
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean,
    val source: String,
    /** Not exposed in the schema; carried so the `GtfsFeed` field resolvers can load revisions. */
    val feedId: Long?,
)

/** GraphQL projection of `eu.transittrack.gtfs.revision.GtfsRevision`. */
data class RevisionDto(
    val id: String,
    val feedCode: String,
    val status: eu.transittrack.gtfs.revision.GtfsRevisionStatus,
    val createdAt: String,
    val activatedAt: String?,
    val supersededAt: String?,
    val contentSha256: String?,
    val byteSize: Long?,
    val filesPresent: List<String>,
    val rowCounts: Map<String, Long>,
    val feedStartDate: String?,
    val feedEndDate: String?,
    val validationSummary: ValidationSummaryDto?,
    val errorMessage: String?,
)

data class ValidationSummaryDto(
    val errorCount: Long,
    val warningCount: Long,
    val notices: List<ValidationNoticeDto>,
)

data class ValidationNoticeDto(
    val rule: String,
    val severity: String,
    val count: Long,
    val sample: String,
)

data class RegisterFeedInput(
    val code: String,
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean?,
    val autoActivate: Boolean?,
)

data class UpdateFeedInput(
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean?,
    val autoActivate: Boolean?,
)
