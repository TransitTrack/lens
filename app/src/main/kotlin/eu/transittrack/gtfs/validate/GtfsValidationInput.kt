package eu.transittrack.gtfs.validate

import java.nio.file.Path

/** The archive being validated. [gtfsZipPath] is the downloaded GTFS zip as-is; the core loader
 * hands it straight to the underlying validator rather than pre-extracting it, so extensions that
 * need per-file access should open it as a zip themselves. */
data class GtfsValidationInput(
    val feedCode: String,
    val gtfsZipPath: Path,
)
