package eu.transittrack.gtfs

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

/**
 * Minimal `@SpringBootConfiguration` entry point for GTFS slice tests.
 *
 * `@DataJpaTest` discovers this by walking up from the test package, so slice tests in `eu.transittrack.gtfs.**` do not depend on the production `eu.transittrack.explorer.Application` (and its GraphQL / web wiring).
 */
@SpringBootConfiguration @EnableAutoConfiguration
class GtfsSliceTestApplication
