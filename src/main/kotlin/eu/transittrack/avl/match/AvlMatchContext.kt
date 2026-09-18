package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component

import eu.transittrack.Point
import eu.transittrack.avl.match.cache.CachedAgencyReader
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
import eu.transittrack.schedule.model.BlockTripRef
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRef
import eu.transittrack.schedule.model.TripRef

/**
 * Per-feed, single-run view over one GTFS revision's derived schedule. Each accessor delegates to a
 * process-wide, revision-scoped cached reader (Caffeine — see `eu.transittrack.avl.match.cache`),
 * fronted by a per-run [HashMap] L1 so the many reports in one match batch don't repeatedly cross
 * the cache proxy.
 *
 * `zone` is the feed's GTFS agency timezone (falling back to [ZoneId.systemDefault] when the feed
 * has no `agency_timezone`) — see [AvlMatchContextFactory.open].
 */
class AvlMatchContext(
    override val revisionId: Long,
    override val zone: ZoneId,
    private val tripReader: CachedTripReader,
    private val patternReader: CachedTripPatternReader,
    private val stopPathReader: CachedStopPathReader,
    private val scheduleReader: CachedScheduleReader,
    private val blockTripReader: CachedBlockTripReader,
    private val serviceDateReader: CachedServiceDateReader,
    private val geometryReader: CachedPatternGeometryReader,
) : MatchContext {
    override fun patternGeometry(tripPatternId: Long): PatternGeometry? = geometryReader.geometry(revisionId, tripPatternId)

    override fun scheduleOf(tripRowId: Long): List<SchedulePoint> = scheduleReader.orderedByTrip(revisionId, tripRowId)

    override fun trip(tripRowId: Long): TripRef? = tripReader.byRowId(revisionId, tripRowId)?.toRef()

    override fun tripByGtfsId(tripId: String): TripRef? = tripReader.byGtfsId(revisionId, tripId)?.toRef()

    override fun blockTripOf(tripRowId: Long): BlockTripRef? = blockTripReader.byTripId(revisionId, tripRowId)?.toRef()

    override fun nextBlockTrip(
        blockPk: Long,
        listIndex: Int,
    ): BlockTripRef? = blockTripReader.orderedByBlock(revisionId, blockPk).firstOrNull { it.listIndex == listIndex + 1 }?.toRef()

    override fun activeServiceIds(date: LocalDate): Set<String> = serviceDateReader.activeServiceIds(revisionId, date)

    override fun candidateTrips(serviceIds: Set<String>): List<TripRef> =
        tripReader.derivedByServices(revisionId, serviceIds.sorted()).map { it.toRef() }

    override fun candidateTripsForRoute(
        routeId: String,
        serviceIds: Set<String>,
    ): List<TripRef> = tripReader.derivedByRouteAndServices(revisionId, routeId, serviceIds.sorted()).map { it.toRef() }

    /** Ordered stop paths of a pattern, used to resolve a descriptor's current stop to a distance along the trip. */
    override fun patternStops(tripPatternId: Long): List<StopPathRef> =
        stopPathReader.orderedByPattern(revisionId, tripPatternId).map { it.toRef() }

    override fun patternExtentWithin(
        tripPatternId: Long,
        p: Point,
        distanceM: Double,
    ): Boolean {
        val pattern = patternReader.byId(revisionId, tripPatternId) ?: return false
        val extent = pattern.extent

        return !extent.isEmpty && extent.isWithinDistance(p, distanceM)
    }
}

private fun Trip.toRef() =
    TripRef(
        id = id!!,
        revisionId = revisionId,
        tripId = tripId,
        routeId = routeId,
        serviceId = serviceId,
        tripHeadsign = tripHeadsign,
        tripShortName = tripShortName,
        directionId = directionId,
        blockId = blockId,
        shapeId = shapeId,
        wheelchairAccessible = wheelchairAccessible,
        bikesAllowed = bikesAllowed,
        tripPatternId = tripPatternId,
        startTimeSec = startTimeSec,
        endTimeSec = endTimeSec,
        frequencyBased = frequencyBased,
        noSchedule = noSchedule,
    )

private fun BlockTrip.toRef() =
    BlockTripRef(
        id = id!!,
        revisionId = revisionId,
        blockId = blockId,
        tripId = tripId,
        listIndex = listIndex,
        layoverAfterSec = layoverAfterSec,
        deadheadAfter = deadheadAfter,
    )

private fun StopPath.toRef() =
    StopPathRef(
        id = id!!,
        revisionId = revisionId,
        tripPatternId = tripPatternId,
        stopPathIndex = stopPathIndex,
        stopId = stopId,
        routeId = routeId,
        stopSeq = stopSeq,
        lengthM = lengthM,
        pathGeometry = pathGeometry,
        pickupType = pickupType,
        dropOffType = dropOffType,
        waitStop = waitStop,
        scheduleAdherenceStop = scheduleAdherenceStop,
        layoverStop = layoverStop,
        breakTimeSec = breakTimeSec,
    )

@Component
class AvlMatchContextFactory(
    private val feeds: GtfsFeedRepository,
    private val revisionService: RevisionService,
    private val agencyReader: CachedAgencyReader,
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
        return openForRevision(revisionId)
    }

    /** Opens a context bound to a specific, already-known revision — e.g. re-opening the revision a
     * persisted `vehicle_match` row was made under, which may no longer be the feed's active one. */
    fun openForRevision(revisionId: Long): AvlMatchContext {
        val zone = agencyReader.timezoneOf(revisionId) ?: ZoneId.systemDefault()
        return AvlMatchContext(
            revisionId,
            zone,
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
