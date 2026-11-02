package eu.transittrack.gtfs.validate

/** A custom GTFS validation rule, run alongside the canonical MobilityData validator. Findings are
 * merged into the feed's [eu.transittrack.gtfs.validate.LoadValidationReport] and, under
 * `transittrack.ingest.strict-validation`, an [GtfsValidationFinding.Severity.ERROR] finding fails
 * the ingest the same way a canonical validator error does. */
fun interface GtfsValidator {
    fun validate(input: GtfsValidationInput): List<GtfsValidationFinding>
}
