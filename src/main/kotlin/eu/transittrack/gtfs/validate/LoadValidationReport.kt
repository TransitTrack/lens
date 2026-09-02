package eu.transittrack.gtfs.validate

import org.mobilitydata.gtfsvalidator.table.GtfsFeedContainer
import tools.jackson.databind.json.JsonMapper

/**
 * Mirrors `org.mobilitydata.gtfsvalidator.notice.SeverityLevel`. `INFO` issues
 * are stored in the report but never contribute to [LoadValidationReport.errorCount]
 * or [LoadValidationReport.warningCount].
 */
enum class Severity { ERROR, WARNING, INFO }

data class ValidationIssue(
    val rule: String,
    val severity: Severity,
    val count: Long,
    val sample: String,
)

data class LoadValidationReport(val gtfsFeedContainer: GtfsFeedContainer? = null,
                                val issues: List<ValidationIssue>) {
    val errorCount: Long get() = issues.filter { it.severity == Severity.ERROR }.sumOf { it.count }
    val warningCount: Long get() = issues.filter { it.severity == Severity.WARNING }.sumOf { it.count }
    val filesPresent: List<String> get() = gtfsFeedContainer?.tables?.filter { t -> !t.isMissingFile }?.map { t -> t.gtfsFilename() }?.toList()?:emptyList()

    fun toJson(): String = MAPPER.writeValueAsString(mapOf("issues" to issues))


    companion object {
        private val MAPPER = JsonMapper.builder().build()
    }
}

class GtfsValidationException(val report: LoadValidationReport) :
    RuntimeException("validation failed with ${report.errorCount} error(s)")
