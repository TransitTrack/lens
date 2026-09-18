package eu.transittrack.gtfs.validate

import java.nio.file.Path
import kotlin.jvm.optionals.getOrDefault

import org.mobilitydata.gtfsvalidator.notice.SeverityLevel
import org.mobilitydata.gtfsvalidator.table.GtfsEntity
import org.mobilitydata.gtfsvalidator.table.GtfsEntityContainer
import org.mobilitydata.gtfsvalidator.table.GtfsFeedContainer
import org.mobilitydata.gtfsvalidator.table.GtfsTableDescriptor
import org.mobilitydata.gtfsvalidator.table.TableStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Mirrors `org.mobilitydata.gtfsvalidator.notice.SeverityLevel`. `INFO` issues are stored in the
 * report but never contribute to [LoadValidationReport.errorCount] or
 * [LoadValidationReport.warningCount].
 */
enum class Severity {
    ERROR,
    WARNING,
    INFO,
}

data class ValidationIssue(
    val rule: String,
    val severity: Severity,
    val count: Long,
    val sample: String,
)

data class LoadValidationReport(
    val gtfsFeedContainer: GtfsFeedContainer? = null,
    val issues: List<ValidationIssue>,
) {
    val errorCount: Long
        get() = issues.filter { it.severity == Severity.ERROR }.sumOf { it.count }

    val warningCount: Long
        get() = issues.filter { it.severity == Severity.WARNING }.sumOf { it.count }

    val filesPresent: List<String>
        get() =
            gtfsFeedContainer
                ?.tables
                ?.filter { t -> !t.isMissingFile }
                ?.map { t -> t.gtfsFilename() }
                ?.toList() ?: emptyList()

    val loaded: Boolean
        get() = gtfsFeedContainer?.isParsedSuccessfully ?: true

    fun toJson(): String = MAPPER.writeValueAsString(mapOf("issues" to issues))

    fun hasFile(filename: String): Boolean = filesPresent.contains(filename)

    fun getFileContent(fileName: String): List<GtfsEntity> =
        gtfsFeedContainer
            ?.getTableForFilename<GtfsEntityContainer<GtfsEntity, GtfsTableDescriptor<GtfsEntity>>>(
                fileName,
            )?.filter { t ->
                t.isParsedSuccessfully && t.tableStatus == TableStatus.PARSABLE_HEADERS_AND_ROWS
            }?.map { it.entities }
            ?.getOrDefault(emptyList()) ?: emptyList()

    companion object {
        private val MAPPER = JsonMapper.builder().build()
    }
}

class GtfsValidationException(
    report: LoadValidationReport,
) : RuntimeException("validation failed with ${report.errorCount} error(s)")

/**
 * Runs the canonical MobilityData GTFS validator against a downloaded archive and maps its `report.json` onto the project's [LoadValidationReport].
 *
 * The validator is invoked with `skipValidatorUpdate = true` so it never makes an outbound call. Its output files (`report.json`, `report.html`, `system_errors.json`) are written under `<workDir>/validation/`; the caller (the ingest pipeline) deletes
 * `workDir` when the run finishes.
 */
@Component
class GtfsFeedLoader(
    private val extraValidators: List<GtfsValidator> = emptyList(),
) {
    private val validationRunner: ValidationRunner = ValidationRunner()

    fun load(
        zipPath: Path,
        feedCode: String = "",
    ): LoadValidationReport {
        val result = validationRunner.validate(zipPath.toUri())
        if (result.status != ValidationRunner.Status.SUCCESS) {
            throw GtfsValidationException(
                LoadValidationReport(
                    null,
                    listOf(ValidationIssue("validator_system_error", Severity.ERROR, 1, "")),
                ),
            )
        }
        val coreIssues =
            result.notices.map { notice ->
                ValidationIssue(
                    rule = notice.code,
                    severity = severityOf(notice.severity),
                    count = notice.totalNotices.toLong(),
                    sample = notice.sampleNotices.firstOrNull()?.toString() ?: "",
                )
            }
        val extraIssues =
            extraValidators.flatMap { validator ->
                validator.validate(GtfsValidationInput(feedCode, zipPath)).map { finding ->
                    ValidationIssue(
                        rule = finding.code,
                        severity = severityOf(finding.severity),
                        count = 1,
                        sample = finding.message,
                    )
                }
            }
        return LoadValidationReport(result.feedContainer, coreIssues + extraIssues)
    }

    private fun severityOf(raw: SeverityLevel): Severity =
        when (raw) {
            SeverityLevel.ERROR -> Severity.ERROR
            SeverityLevel.INFO -> Severity.INFO
            else -> Severity.WARNING
        }

    private fun severityOf(raw: GtfsValidationFinding.Severity): Severity =
        when (raw) {
            GtfsValidationFinding.Severity.ERROR -> Severity.ERROR
            GtfsValidationFinding.Severity.WARNING -> Severity.WARNING
        }
}
