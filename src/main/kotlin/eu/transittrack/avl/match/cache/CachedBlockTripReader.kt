package eu.transittrack.avl.match.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.BlockTripRepository

/** Revision-scoped, cached block-trip lookups. */
@Component
class CachedBlockTripReader(
    private val blockTrips: BlockTripRepository,
) {
    @Cacheable(AvlCaches.BLOCK_TRIP_BY_TRIP, key = "#revisionId + ':' + #tripRowId")
    fun byTripId(
        revisionId: Long,
        tripRowId: Long,
    ): BlockTrip? = blockTrips.findByTripId(revisionId, tripRowId)

    @Cacheable(AvlCaches.BLOCK_TRIPS_BY_BLOCK, key = "#revisionId + ':' + #blockPk")
    fun orderedByBlock(
        revisionId: Long,
        blockPk: Long,
    ): List<BlockTrip> = blockTrips.findByBlockIdOrdered(revisionId, blockPk)
}
