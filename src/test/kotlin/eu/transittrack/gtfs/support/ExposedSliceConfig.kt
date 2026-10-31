package eu.transittrack.gtfs.support

import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import

import eu.transittrack.db.ExposedExceptionTranslator
import eu.transittrack.db.bridge.ExposedBridgeConfig
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository

/**
 * The Exposed repositories (and the JPA bridge they run through) for `@DataJpaTest` slices, which
 * do not component-scan `@Repository` classes. Every area ported to Exposed adds its repositories here.
 */
@EnableAspectJAutoProxy
@Import(
    ExposedBridgeConfig::class,
    ExposedExceptionTranslator::class,
    GtfsFeedRepository::class,
    GtfsRevisionRepository::class,
    DraftEditRepository::class,
)
class ExposedSliceConfig
