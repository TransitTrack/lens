package eu.transittrack.gtfs.validate

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
