package eu.transittrack

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

/**
 * Minimal `@SpringBootConfiguration` entry point for schedule-model slice tests.
 *
 * `@DataJpaTest` discovers this by walking up from `eu.transittrack.schedule.**`,
 * so schedule slice tests do not depend on the production
 * `eu.transittrack.explorer.Application` (and its GraphQL / web wiring). It sits
 * at the `eu.transittrack` root so the auto-configuration package covers both the
 * `eu.transittrack.schedule.*` entities and the cross-cutting
 * `eu.transittrack.gtfs.*` feed / revision entities used for FK seeding.
 *
 * Slice tests under `eu.transittrack.gtfs.**` still resolve the nearer
 * `eu.transittrack.gtfs.GtfsSliceTestApplication` first.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class ScheduleSliceTestApplication
