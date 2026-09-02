package eu.transittrack.gtfs.validate

import org.mobilitydata.gtfsvalidator.notice.SeverityLevel
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path

/**
 * Runs the canonical MobilityData GTFS validator against a downloaded archive
 * and maps its `report.json` onto the project's [LoadValidationReport].
 *
 * The validator is invoked with `skipValidatorUpdate = true` so it never makes
 * an outbound call. Its output files (`report.json`, `report.html`,
 * `system_errors.json`) are written under `<workDir>/validation/`; the caller
 * (the ingest pipeline) deletes `workDir` when the run finishes.
 */
@Component
class GtfsFeedLoader(private val jsonMapper: JsonMapper) {

    private val validationRunner: ValidationRunner = ValidationRunner()

    fun load(zipPath: Path): LoadValidationReport {
        val result = validationRunner.validate(zipPath.toUri())
        if (result.status != ValidationRunner.Status.SUCCESS) {
            throw GtfsValidationException(
                LoadValidationReport(null,
                    listOf(ValidationIssue("validator_system_error", Severity.ERROR, 1, "")),
                ),
            )
        }
        return LoadValidationReport(result.feedContainer, result.notices.map { notice -> ValidationIssue(
            rule = notice.code,
            severity = severityOf(notice.severity),
            count = notice.totalNotices.toLong(),
            sample = notice.sampleNotices.firstOrNull()?.toString() ?: ""
        ) })
    }

    private fun severityOf(raw: SeverityLevel): Severity = when (raw) {
        SeverityLevel.ERROR -> Severity.ERROR
        SeverityLevel.INFO -> Severity.INFO
        else -> Severity.WARNING
    }
}
