package eu.transittrack.gtfs.validate

import tools.jackson.databind.json.JsonMapper

/**
 * Mirrors `org.mobilitydata.gtfsvalidator.notice.SeverityLevel`. `INFO` issues
 * are stored in the report but never contribute to [ValidationReport.errorCount]
 * or [ValidationReport.warningCount].
 */
enum class Severity { ERROR, WARNING, INFO }

data class ValidationIssue(
    val rule: String,
    val severity: Severity,
    val count: Long,
    val sample: String,
)

data class ValidationReport(val issues: List<ValidationIssue>) {
    val errorCount: Long get() = issues.filter { it.severity == Severity.ERROR }.sumOf { it.count }
    val warningCount: Long get() = issues.filter { it.severity == Severity.WARNING }.sumOf { it.count }

    fun toJson(): String = MAPPER.writeValueAsString(mapOf("issues" to issues))

    companion object {
        private val MAPPER = JsonMapper.builder().build()
    }
}

class GtfsValidationException(val report: ValidationReport) :
    RuntimeException("validation failed with ${report.errorCount} error(s)")
