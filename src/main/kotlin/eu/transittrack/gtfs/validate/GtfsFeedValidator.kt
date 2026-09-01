package eu.transittrack.gtfs.validate

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.nio.file.Path
import org.mobilitydata.gtfsvalidator.runner.ApplicationType
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig
import org.mobilitydata.gtfsvalidator.util.VersionResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * Runs the canonical MobilityData GTFS validator against a downloaded archive
 * and maps its `report.json` onto the project's [ValidationReport].
 *
 * The validator is invoked with `skipValidatorUpdate = true` so it never makes
 * an outbound call. Its output files (`report.json`, `report.html`,
 * `system_errors.json`) are written under `<workDir>/validation/`; the caller
 * (the ingest pipeline) deletes `workDir` when the run finishes.
 */
@Component
class GtfsFeedValidator(private val jsonMapper: JsonMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Cheap to construct; holds no per-run state.
    private val versionResolver = VersionResolver(ApplicationType.CLI)

    fun validate(zipPath: Path, workDir: Path): ValidationReport {
        val outputDir = workDir.resolve("validation")
        val config = ValidationRunnerConfig.builder()
            .setGtfsSource(zipPath.toUri())
            .setOutputDirectory(outputDir)
            .setSkipValidatorUpdate(true)
            .build()

        val status = ValidationRunner(versionResolver).run(config)
        if (status != ValidationRunner.Status.SUCCESS) {
            val detail = readFirstSystemError(outputDir)
            log.warn("gtfs-validator did not complete: status={} detail={}", status, detail)
            throw GtfsValidationException(
                ValidationReport(
                    listOf(ValidationIssue("validator_system_error", Severity.ERROR, 1, detail)),
                ),
            )
        }

        val reportFile = outputDir.resolve("report.json").toFile()
        val raw = jsonMapper.readValue(reportFile, RawReport::class.java)
        return ValidationReport(
            raw.notices.map { n ->
                ValidationIssue(
                    rule = n.code,
                    severity = severityOf(n.severity),
                    count = n.totalNotices.toLong(),
                    sample = n.sampleNotices.firstOrNull()?.let { jsonMapper.writeValueAsString(it) } ?: "",
                )
            },
        )
    }

    private fun severityOf(raw: String): Severity = when (raw.uppercase()) {
        "ERROR" -> Severity.ERROR
        "INFO" -> Severity.INFO
        else -> Severity.WARNING
    }

    private fun readFirstSystemError(outputDir: Path): String {
        val file = outputDir.resolve("system_errors.json").toFile()
        if (!file.exists()) return "validator status was not SUCCESS"
        return runCatching {
            val sys = jsonMapper.readValue(file, RawReport::class.java)
            sys.notices.firstOrNull()?.let { "${it.code}: ${it.sampleNotices.firstOrNull() ?: ""}" }
                ?: file.readText().take(500)
        }.getOrElse { file.readText().take(500) }
    }

    /**
     * Minimal projection of the validator's `report.json` / `system_errors.json`.
     * Jackson 3 does not fail on unknown properties by default, so the top-level
     * `summary` object and any notice fields we do not read are ignored.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RawReport(val notices: List<RawNotice> = emptyList())

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class RawNotice(
        val code: String = "",
        val severity: String = "",
        val totalNotices: Int = 0,
        val sampleNotices: List<Map<String, Any?>> = emptyList(),
    )
}
