package eu.transittrack.avl.read

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.rest.generated.model.LatLon
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.TripPatternRepository

/** The live vehicles of a feed with just the trip / route / block / pattern fields the map draws. */
@Service
class MapVehicleReadService(
    private val read: AvlReadService,
    private val trips: TripRepository,
    private val routes: RouteRepository,
    private val blocks: BlockRepository,
    private val patterns: TripPatternRepository,
) {
    /**
     * One batched read per entity kind (trips, routes, blocks, patterns) over every vehicle, not four
     * lookups per vehicle: this endpoint runs on each map poll, for the whole fleet.
     */
    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<MapVehicle> {
        val states = read.vehicleStates(feedCode, matchedOnly)
        val located = states.filter { it.revisionId != null }
        val tripsById = trips.findAllById(located.mapNotNull { it.tripRowId }.distinct()).associateBy { it.id }
        val blocksById = blocks.findAllById(located.mapNotNull { it.blockPk }.distinct()).associateBy { it.id }
        val patternsById = patterns.findAllById(located.mapNotNull { it.tripPatternId }.distinct()).associateBy { it.id }
        val routesByKey =
            tripsById.values
                .groupBy({ it.revisionId }, { it.routeId })
                .flatMap { (revision, routeIds) -> routes.findByRouteIds(revision, routeIds.distinct()) }
                .associateBy { it.revisionId to it.routeId }

        return states.map { state ->
            val hasRevision = state.revisionId != null
            val trip = if (hasRevision) state.tripRowId?.let(tripsById::get) else null
            val route = trip?.let { routesByKey[it.revisionId to it.routeId] }
            val block = if (hasRevision) state.blockPk?.let(blocksById::get) else null
            val pattern = if (hasRevision) state.tripPatternId?.let(patternsById::get) else null
            MapVehicle(
                state.vehicleId, state.vehicleLabel, state.reportTs.toString(), LatLon(state.lat, state.lon), state.bearing,
                state.matched, state.stale, state.scheduleAdherenceSec, state.stopPathIndex,
                state.distanceAlongTripM, state.speedMps,
                pattern?.let { MapPattern(it.lengthM) },
                trip?.let {
                    MapTrip(
                        it.tripId, it.routeId, it.tripHeadsign,
                        route?.let { r -> MapRoute(r.routeShortName, r.routeColor, r.routeTextColor) },
                    )
                },
                block?.let { MapBlock(it.blockId, it.serviceId) },
            )
        }
    }
}

data class MapVehicle(
    val vehicleId: String,
    val label: String?,
    val reportTs: String,
    val position: LatLon,
    val bearing: Double?,
    val matched: Boolean,
    val stale: Boolean,
    val scheduleAdherenceSec: Int?,
    val stopPathIndex: Int?,
    val distanceAlongTripM: Double?,
    val speedMps: Double?,
    val pattern: MapPattern?,
    val trip: MapTrip?,
    val block: MapBlock?,
)

data class MapPattern(
    val lengthM: Double?,
)

data class MapTrip(
    val tripId: String,
    val routeId: String,
    val tripHeadsign: String?,
    val route: MapRoute?,
)

data class MapRoute(
    val routeShortName: String?,
    val routeColor: String?,
    val routeTextColor: String?,
)

data class MapBlock(
    val blockId: String,
    val serviceId: String,
)
