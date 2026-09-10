package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component

import eu.transittrack.Point
import eu.transittrack.avl.match.cache.CachedBlockTripReader
import eu.transittrack.avl.match.cache.CachedPatternGeometryReader
import eu.transittrack.avl.match.cache.CachedScheduleReader
import eu.transittrack.avl.match.cache.CachedServiceDateReader
import eu.transittrack.avl.match.cache.CachedStopPathReader
import eu.transittrack.avl.match.cache.CachedTripPatternReader
import eu.transittrack.avl.match.cache.CachedTripReader
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.StopPath

/**
 * Per-feed, single-run view over one GTFS revision's derived schedule. Each accessor delegates to a
 * process-wide, revision-scoped cached reader (EhCache — see `eu.transittrack.avl.match.cache`),
 * fronted by a per-run [HashMap] L1 so the many reports in one match batch don't repeatedly cross
 * the cache proxy.
 *
 * Ruling: `zone = ZoneId.systemDefault()` — there is no per-feed timezone yet, matching the
 * service-day assumptions of `GtfsIngestScheduler` and `ServiceDateResolver`.
 */
class AvlMatchContext(
    val revisionId: Long,
    val zone: ZoneId,
    private val tripReader: CachedTripReader,
    private val patternReader: CachedTripPatternReader,
    private val stopPathReader: CachedStopPathReader,
    private val scheduleReader: CachedScheduleReader,
    private val blockTripReader: CachedBlockTripReader,
    private val serviceDateReader: CachedServiceDateReader,
    private val geometryReader: CachedPatternGeometryReader,
) {
    fun patternGeometry(tripPatternId: Long): PatternGeometry? = geometryReader.geometry(revisionId, tripPatternId)

    fun scheduleOf(tripRowId: Long): List<SchedulePoint> = scheduleReader.orderedByTrip(revisionId, tripRowId)

    fun trip(tripRowId: Long): Trip? = tripReader.byRowId(revisionId, tripRowId)

    fun tripByGtfsId(tripId: String): Trip? = tripReader.byGtfsId(revisionId, tripId)

    fun blockTripOf(tripRowId: Long): BlockTrip? = blockTripReader.byTripId(revisionId, tripRowId)

    fun nextBlockTrip(
        blockPk: Long,
        listIndex: Int,
    ): BlockTrip? = blockTripReader.orderedByBlock(revisionId, blockPk).firstOrNull { it.listIndex == listIndex + 1 }

    fun activeServiceIds(date: LocalDate): Set<String> = serviceDateReader.activeServiceIds(revisionId, date)

    fun candidateTrips(serviceIds: Set<String>): List<Trip> = tripReader.derivedByServices(revisionId, serviceIds.sorted())

    fun candidateTripsForRoute(
        routeId: String,
        serviceIds: Set<String>,
    ): List<Trip> = tripReader.derivedByRouteAndServices(revisionId, routeId, serviceIds.sorted())

    /** Ordered stop paths of a pattern, used to resolve a descriptor's current stop to a distance along the trip. */
    fun patternStops(tripPatternId: Long): List<StopPath> = stopPathReader.orderedByPattern(revisionId, tripPatternId)

    fun patternExtentWithin(
        tripPatternId: Long,
        p: Point,
        distanceM: Double,
    ): Boolean {
        val pattern = patternReader.byId(revisionId, tripPatternId) ?: return false
        val extent = pattern.extent
        return if (extent.isEmpty) {
            false
        } else {
            extent.isWithinDistance(p, distanceM)
        }
    }
}

@Component
class AvlMatchContextFactory(
    private val feeds: GtfsFeedRepository,
    private val revisionService: RevisionService,
    private val tripReader: CachedTripReader,
    private val patternReader: CachedTripPatternReader,
    private val stopPathReader: CachedStopPathReader,
    private val scheduleReader: CachedScheduleReader,
    private val blockTripReader: CachedBlockTripReader,
    private val serviceDateReader: CachedServiceDateReader,
    private val geometryReader: CachedPatternGeometryReader,
) {
    /** Opens a context bound to the active revision of `feed.gtfsFeedCode`; `null` when none. */
    fun open(feed: AvlFeed): AvlMatchContext? {
        val gtfsFeed = feeds.findByCode(feed.gtfsFeedCode) ?: return null
        val revisionId = revisionService.activeRevisionId(gtfsFeed.id!!) ?: return null
        return AvlMatchContext(
            revisionId,
            ZoneId.systemDefault(),
            tripReader,
            patternReader,
            stopPathReader,
            scheduleReader,
            blockTripReader,
            serviceDateReader,
            geometryReader,
        )
    }
}
