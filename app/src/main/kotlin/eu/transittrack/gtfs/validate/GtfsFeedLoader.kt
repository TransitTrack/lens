package eu.transittrack.gtfs.validate

import java.nio.file.Path

import io.github.transittrack.gtfsanalyzer.EngineJson
import io.github.transittrack.gtfsanalyzer.EngineResult
import io.github.transittrack.gtfsanalyzer.FatalError
import io.github.transittrack.gtfsanalyzer.GtfsAnalyzer
import io.github.transittrack.gtfsanalyzer.SDK_VERSION
import io.github.transittrack.gtfsanalyzer.ValidationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import io.github.transittrack.gtfsanalyzer.Severity as EngineSeverity

/**
 * The project's three-level severity. The analyzer's five levels map onto it as CRITICAL/HIGH →
 * [ERROR], MEDIUM/LOW → [WARNING], INFO → [INFO]. `INFO` issues never contribute to
 * [LoadValidationReport.errorCount] or [LoadValidationReport.warningCount].
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

/**
 * The outcome of validating one archive: the gtfs-analyzer [engine] result (either a full
 * [ValidationResult] or a [FatalError]) plus findings from registered [GtfsValidator] extensions.
 *
 * [toJson] is what gets stored in `gtfs_revision.validation_report`: a versioned envelope around the
 * analyzer's own wire format, so the full report (per-notice detail, R1–R9 reports, feed metrics)
 * can be read back with the SDK types.
 */
class LoadValidationReport(
    val engine: EngineResult,
    val extensionFindings: List<GtfsValidationFinding> = emptyList(),
) {
    val fatal: FatalError? get() = (engine as? EngineResult.Fatal)?.error

    val result: ValidationResult? get() = (engine as? EngineResult.Ok)?.result

    /**
     * The analyzer's R1 verdict, also false when any extension reported an `ERROR` (the extension API
     * contract: an ERROR finding blocks like a canonical validator error). Never true for a fatal result.
     */
    val publishable: Boolean
        get() =
            (result?.reports?.r1?.publishable ?: false) &&
                extensionFindings.none { it.severity == GtfsValidationFinding.Severity.ERROR }

    /** Rows the analyzer counted in [fileName]; 0 if it didn't see the file. */
    fun analyzedRows(fileName: String): Long =
        result
            ?.metrics
            ?.fileStats
            ?.find { it.name == fileName }
            ?.rows ?: 0

    /** Files the analyzer could not read (missing or unparseable); only set on a PARTIAL result. */
    val unavailableFiles: List<String> get() = result?.partial?.unavailableFiles.orEmpty()

    /**
     * One issue per analyzer rule (first-seen order), then one per extension finding. A rule's
     * count is the engine's true total from `capped_totals` when the notice list was capped.
     */
    val issues: List<ValidationIssue> by lazy {
        val engineIssues =
            fatal?.let { listOf(ValidationIssue(it.code.name, Severity.ERROR, 1, it.message)) }
                ?: result!!.notices.groupBy { it.ruleId }.map { (ruleId, notices) ->
                    ValidationIssue(
                        rule = ruleId,
                        severity = severityOf(notices.first().severity),
                        count = result!!.cappedTotals[ruleId]?.toLong() ?: notices.size.toLong(),
                        sample = notices.first().message,
                    )
                }
        engineIssues +
            extensionFindings.map { ValidationIssue(it.code, severityOf(it.severity), 1, it.message) }
    }

    val errorCount: Long get() = issues.filter { it.severity == Severity.ERROR }.sumOf { it.count }

    val warningCount: Long get() = issues.filter { it.severity == Severity.WARNING }.sumOf { it.count }

    fun toJson(): String =
        EngineJson.encodeToString(
            StoredReport.serializer(),
            StoredReport(
                format = FORMAT,
                analyzer = StoredAnalyzer(SDK_VERSION, engine),
                extensionFindings =
                    extensionFindings.map { StoredFinding(it.code, it.severity.name, it.message, it.file, it.entityId) },
            ),
        )

    @Serializable
    private class StoredReport(
        val format: Int,
        val analyzer: StoredAnalyzer,
        val extensionFindings: List<StoredFinding>,
    )

    @Serializable
    private class StoredAnalyzer(
        val version: String,
        val result: EngineResult,
    )

    @Serializable
    private class StoredFinding(
        val code: String,
        val severity: String,
        val message: String,
        val file: String? = null,
        val entityId: String? = null,
    )

    companion object {
        private const val FORMAT = 2
        private val log = LoggerFactory.getLogger(LoadValidationReport::class.java)

        /**
         * Reads a stored report back; `null` for reports written before the analyzer (no `format`) or
         * ones that no longer decode (logged).
         */
        fun fromJson(json: String): LoadValidationReport? =
            runCatching {
                val format = EngineJson
                    .parseToJsonElement(json)
                    .jsonObject["format"]
                    ?.jsonPrimitive
                    ?.intOrNull
                if (format != FORMAT) return null
                val stored = EngineJson.decodeFromString(StoredReport.serializer(), json)
                LoadValidationReport(
                    stored.analyzer.result,
                    stored.extensionFindings.map {
                        GtfsValidationFinding(
                            GtfsValidationFinding.Severity.valueOf(it.severity),
                            it.code,
                            it.message,
                            it.file,
                            it.entityId,
                        )
                    },
                )
            }.onFailure { log.warn("stored validation report could not be decoded: {}", it.message) }
                .getOrNull()

        private fun severityOf(raw: EngineSeverity): Severity =
            when (raw) {
                EngineSeverity.CRITICAL, EngineSeverity.HIGH -> Severity.ERROR
                EngineSeverity.MEDIUM, EngineSeverity.LOW -> Severity.WARNING
                EngineSeverity.INFO -> Severity.INFO
            }

        private fun severityOf(raw: GtfsValidationFinding.Severity): Severity =
            when (raw) {
                GtfsValidationFinding.Severity.ERROR -> Severity.ERROR
                GtfsValidationFinding.Severity.WARNING -> Severity.WARNING
            }
    }
}

class GtfsValidationException(
    report: LoadValidationReport,
) : RuntimeException("validation failed with ${report.errorCount} error(s)")

/**
 * Validates a downloaded archive with gtfs-analyzer (the Rust validation engine, bundled as a
 * native library) and runs any registered [GtfsValidator] extensions over it.
 *
 * Validation only: parsing is done separately by the streaming reader in `eu.transittrack.gtfs.parse`.
 * A fatal engine result (unreadable zip, …) is returned in the report, never thrown; callers decide
 * whether to stop.
 */
@Component
class GtfsFeedLoader(
    private val extraValidators: List<GtfsValidator> = emptyList(),
) {
    fun load(
        zipPath: Path,
        feedCode: String = "",
    ): LoadValidationReport {
        val validationResult = GtfsAnalyzer.validate(kotlinx.io.files.Path(zipPath.toString()))
        val findings = extraValidators.flatMap { it.validate(GtfsValidationInput(feedCode, zipPath)) }
        return LoadValidationReport(validationResult, findings)
    }
}
