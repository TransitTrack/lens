package eu.transittrack.gtfs.api.dto

/** GraphQL projection of `eu.transittrack.gtfs.feed.GtfsFeed`. */
data class GtfsFeedDto(
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
data class GtfsRevisionDto(
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
    val validationSummary: GtfsValidationSummaryDto?,
    val errorMessage: String?,
)

data class GtfsValidationSummaryDto(val errorCount: Long, val warningCount: Long)

data class RegisterGtfsFeedInput(
    val code: String,
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean?,
    val autoActivate: Boolean?,
)

data class UpdateGtfsFeedInput(
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean?,
    val autoActivate: Boolean?,
)
