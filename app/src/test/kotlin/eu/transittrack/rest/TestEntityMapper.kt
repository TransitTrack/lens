package eu.transittrack.rest

import org.mockito.kotlin.mock

import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.TripPatternRepository

/** A [RestEntityMapper] over mock repositories; pass the ones a test needs to stub. */
fun testEntityMapper(
    trips: TripRepository = mock(),
    patterns: TripPatternRepository = mock(),
) = RestEntityMapper(
    agencies = mock(), routes = mock(), stops = mock(), trips = trips, stopTimes = mock(), shapes = mock(), shapePoints = mock(),
    levels = mock(), patterns = patterns, stopPaths = mock(), travelTimes = mock(), scheduleTimes = mock(), blocks = mock(),
    blockTrips = mock(),
)
