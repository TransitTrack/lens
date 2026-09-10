package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository

/**
 * Revision-scoped, cached trip lookups for the AVL match path. `serviceIds` list arguments must be
 * passed sorted by the caller so the cache key is order-stable.
 */
@Component
class CachedTripReader(
    private val trips: TripRepository,
) {
    @Cacheable(AvlCaches.TRIP_BY_ROW_ID, key = "#revisionId + ':' + #tripRowId")
    fun byRowId(
        revisionId: Long,
        tripRowId: Long,
    ): Trip? = trips.findById(tripRowId).orElse(null)

    @Cacheable(AvlCaches.TRIP_BY_GTFS_ID, key = "#revisionId + ':' + #gtfsTripId")
    fun byGtfsId(
        revisionId: Long,
        gtfsTripId: String,
    ): Trip? = trips.findByTripId(revisionId, gtfsTripId)

    @Cacheable(AvlCaches.TRIPS_BY_SERVICES, key = "#revisionId + ':' + #serviceIds")
    fun derivedByServices(
        revisionId: Long,
        serviceIds: List<String>,
    ): List<Trip> = trips.findDerivedByServices(revisionId, serviceIds)

    @Cacheable(AvlCaches.TRIPS_BY_ROUTE_SERVICES, key = "#revisionId + ':' + #routeId + ':' + #serviceIds")
    fun derivedByRouteAndServices(
        revisionId: Long,
        routeId: String,
        serviceIds: List<String>,
    ): List<Trip> = trips.findDerivedByRouteAndServices(revisionId, routeId, serviceIds)
}
