package eu.transittrack.avl.match

import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.Point
import eu.transittrack.Polyline
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.ServiceDateResolver

/**
 * Per-feed, single-run view over one GTFS revision's derived schedule. Every accessor is a cached
 * delegation to a revision-scoped repository; all caches are plain [HashMap]s because one context
 * serves one AVL processor run on one thread.
 *
 * Ruling: `zone = ZoneId.systemDefault()` — there is no per-feed timezone yet, matching the
 * service-day assumptions of `GtfsIngestScheduler` and `ServiceDateResolver`.
 */
class AvlMatchContext(
    val revisionId: Long,
    val zone: ZoneId,
    private val trips: TripRepository,
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val blocks: BlockRepository,
    private val blockTrips: BlockTripRepository,
    private val serviceDates: ServiceDateResolver,
    private val json: JsonMapper,
) {
    private val geometryCache = HashMap<Long, PatternGeometry?>()
    private val scheduleCache = HashMap<Long, List<SchedulePoint>>()
    private val tripByRowIdCache = HashMap<Long, Trip?>()
    private val tripByGtfsIdCache = HashMap<String, Trip?>()
    private val blockTripCache = HashMap<Long, BlockTrip?>()
    private val serviceIdsCache = HashMap<LocalDate, Set<String>>()

    fun patternGeometry(tripPatternId: Long): PatternGeometry? = geometryCache.getOrPut(tripPatternId) { buildGeometry(tripPatternId) }

    fun scheduleOf(tripRowId: Long): List<SchedulePoint> =
        scheduleCache.getOrPut(tripRowId) {
            scheduleTimes.findByTripOrdered(revisionId, tripRowId).map {
                SchedulePoint(it.stopPathIndex, it.arrivalSec, it.departureSec)
            }
        }

    fun trip(tripRowId: Long): Trip? = tripByRowIdCache.getOrPut(tripRowId) { trips.findById(tripRowId).orElse(null) }

    fun tripByGtfsId(tripId: String): Trip? = tripByGtfsIdCache.getOrPut(tripId) { trips.findByTripId(revisionId, tripId) }

    fun blockTripOf(tripRowId: Long): BlockTrip? = blockTripCache.getOrPut(tripRowId) { blockTrips.findByTripId(revisionId, tripRowId) }

    fun nextBlockTrip(
        blockPk: Long,
        listIndex: Int,
    ): BlockTrip? = blockTrips.findByBlockIdOrdered(revisionId, blockPk).firstOrNull { it.listIndex == listIndex + 1 }

    fun activeServiceIds(date: LocalDate): Set<String> = serviceIdsCache.getOrPut(date) { serviceDates.activeServiceIds(revisionId, date) }

    fun candidateTrips(serviceIds: Set<String>): List<Trip> = trips.findDerivedByServices(revisionId, serviceIds)

    fun patternExtentWithin(
        tripPatternId: Long,
        p: Point,
        distanceM: Double,
    ): Boolean {
        val pattern = patterns.findById(tripPatternId).orElse(null) ?: return false
        val extent = pattern.extent
        return if (extent.isEmpty) false else extent.isWithinDistance(p, distanceM)
    }

    private fun buildGeometry(tripPatternId: Long): PatternGeometry? {
        val paths = stopPaths.findByTripPatternOrdered(revisionId, tripPatternId)
        if (paths.isEmpty()) return null
        val pts = ArrayList<Point>()
        val cum = DoubleArray(paths.size)
        var acc = 0.0
        for ((i, sp) in paths.withIndex()) {
            cum[i] = acc
            acc += sp.lengthM
            val raw = sp.pathGeometry ?: continue

            // Stored as [[lon,lat],...]; parsed loosely, mirroring ScheduleReadService.parseGeometry.
            @Suppress("UNCHECKED_CAST")
            val coords = json.readValue(raw, List::class.java) as List<List<Number>>
            for (c in coords) {
                val p = Point(c[1].toDouble(), c[0].toDouble())
                if (pts.isEmpty() || pts.last() != p) pts.add(p)
            }
        }
        if (pts.size < 2) return null
        return PatternGeometry(Polyline(pts), cum)
    }
}

@Component
class AvlMatchContextFactory(
    private val feeds: GtfsFeedRepository,
    private val revisionService: RevisionService,
    private val trips: TripRepository,
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val blocks: BlockRepository,
    private val blockTrips: BlockTripRepository,
    private val serviceDates: ServiceDateResolver,
    private val json: JsonMapper,
) {
    /** Opens a context bound to the active revision of `feed.gtfsFeedCode`; `null` when none. */
    fun open(feed: AvlFeed): AvlMatchContext? {
        val gtfsFeed = feeds.findByCode(feed.gtfsFeedCode) ?: return null
        val revisionId = revisionService.activeRevisionId(gtfsFeed.id!!) ?: return null
        return AvlMatchContext(
            revisionId,
            ZoneId.systemDefault(),
            trips,
            patterns,
            stopPaths,
            scheduleTimes,
            blocks,
            blockTrips,
            serviceDates,
            json,
        )
    }
}
