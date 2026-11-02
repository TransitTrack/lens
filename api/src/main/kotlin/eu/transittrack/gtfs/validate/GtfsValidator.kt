package eu.transittrack.gtfs.validate

import java.nio.file.Path

/** The archive being validated. [gtfsZipPath] is the downloaded GTFS zip as-is; the core loader
 * hands it straight to the underlying validator rather than pre-extracting it, so extensions that
 * need per-file access should open it as a zip themselves. */
data class GtfsValidationInput(
    val feedCode: String,
    val gtfsZipPath: Path,
)

data class GtfsValidationFinding(
    val severity: Severity,
    val code: String,
    val message: String,
    val file: String? = null,
    val entityId: String? = null,
) {
    enum class Severity {
        ERROR,
        WARNING,
    }
}

/** A custom GTFS validation rule, run alongside the canonical MobilityData validator. Findings are
 * merged into the feed's [eu.transittrack.gtfs.validate.LoadValidationReport] and, under
 * `transittrack.ingest.strict-validation`, an [GtfsValidationFinding.Severity.ERROR] finding fails
 * the ingest the same way a canonical validator error does. */
fun interface GtfsValidator {
    fun validate(input: GtfsValidationInput): List<GtfsValidationFinding>
}
