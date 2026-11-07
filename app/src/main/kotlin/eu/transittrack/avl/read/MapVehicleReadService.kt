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
    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<MapVehicle> =
        read.vehicleStates(feedCode, matchedOnly).map { state ->
            val revision = state.revisionId
            val trip = if (revision != null) state.tripRowId?.let { trips.findById(it) } else null
            val route = if (revision != null) trip?.let { routes.findByRouteId(revision, it.routeId) } else null
            val block = if (revision != null) state.blockPk?.let { blocks.findById(it) } else null
            val pattern = if (revision != null) state.tripPatternId?.let { patterns.findById(it) } else null
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
