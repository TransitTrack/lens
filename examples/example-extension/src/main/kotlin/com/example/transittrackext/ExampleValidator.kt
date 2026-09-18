package com.example.transittrackext

import eu.transittrack.gtfs.validate.GtfsValidationFinding
import eu.transittrack.gtfs.validate.GtfsValidationInput
import eu.transittrack.gtfs.validate.GtfsValidator

class ExampleValidator : GtfsValidator {
    override fun validate(input: GtfsValidationInput): List<GtfsValidationFinding> = emptyList()
}
