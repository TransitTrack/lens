package eu.transittrack.gtfs.support

import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.db.ExposedExceptionTranslator
import eu.transittrack.db.bridge.ExposedBridgeConfig
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftRowCopier
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.AreaRepository
import eu.transittrack.gtfs.model.AttributionRepository
import eu.transittrack.gtfs.model.BookingRuleRepository
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FareAttributeRepository
import eu.transittrack.gtfs.model.FareLegJoinRuleRepository
import eu.transittrack.gtfs.model.FareLegRuleRepository
import eu.transittrack.gtfs.model.FareMediaRepository
import eu.transittrack.gtfs.model.FareProductRepository
import eu.transittrack.gtfs.model.FareRuleRepository
import eu.transittrack.gtfs.model.FareTransferRuleRepository
import eu.transittrack.gtfs.model.FeedInfoRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.LevelRepository
import eu.transittrack.gtfs.model.LocationGroupRepository
import eu.transittrack.gtfs.model.LocationGroupStopRepository
import eu.transittrack.gtfs.model.LocationRepository
import eu.transittrack.gtfs.model.NetworkRepository
import eu.transittrack.gtfs.model.PathwayRepository
import eu.transittrack.gtfs.model.RiderCategoryRepository
import eu.transittrack.gtfs.model.RouteNetworkRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopAreaRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TimeframeRepository
import eu.transittrack.gtfs.model.TransferRepository
import eu.transittrack.gtfs.model.TranslationRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository

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
    DraftRowCopier::class,
    AgencyRepository::class,
    AreaRepository::class,
    AttributionRepository::class,
    BookingRuleRepository::class,
    CalendarDateRepository::class,
    CalendarRepository::class,
    FareAttributeRepository::class,
    FareLegJoinRuleRepository::class,
    FareLegRuleRepository::class,
    FareMediaRepository::class,
    FareProductRepository::class,
    FareRuleRepository::class,
    FareTransferRuleRepository::class,
    FeedInfoRepository::class,
    FrequencyRepository::class,
    LevelRepository::class,
    LocationGroupRepository::class,
    LocationGroupStopRepository::class,
    LocationRepository::class,
    NetworkRepository::class,
    PathwayRepository::class,
    RiderCategoryRepository::class,
    RouteNetworkRepository::class,
    RouteRepository::class,
    ShapePointRepository::class,
    ShapeRepository::class,
    StopAreaRepository::class,
    StopRepository::class,
    StopTimeRepository::class,
    TimeframeRepository::class,
    TransferRepository::class,
    TranslationRepository::class,
    TripRepository::class,
    TripPatternRepository::class,
    StopPathRepository::class,
    ScheduleTimeRepository::class,
    BlockRepository::class,
    BlockTripRepository::class,
    TravelTimesForStopPathRepository::class,
    OptimizationRunRepository::class,
    OptimizationRecommendationRepository::class,
    AvlFeedRepository::class,
    AvlReportRowRepository::class,
    VehicleMatchRepository::class,
    VehicleStateRepository::class,
)
class ExposedSliceConfig
