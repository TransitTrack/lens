package eu.transittrack.schedule.derive

data class BlockTripInput(
    val schedTripId: Long,
    val blockId: String,
    val serviceId: String,
    val routeId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val firstStopId: String,
    val lastStopId: String,
)

data class BlockTripUpdate(
    val schedTripId: Long,
    val listIndex: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
)

data class BlockResult(
    val blockId: String,
    val serviceId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val tripCount: Int,
    val routeIds: List<String>,
    val tripUpdates: List<BlockTripUpdate>,
)

/** Reconstructs a vehicle's ordered run of trips for one (blockId, serviceId). */
object BlockBuilder {
    /**
     * Precondition: the caller must have filtered out trips with a blank/absent
     * `block_id` (and frequency-based trips, whose start times are 0-based offsets and
     * would sort to the front of every block).
     */
    fun build(trips: List<BlockTripInput>): List<BlockResult> =
        trips.groupBy { it.blockId to it.serviceId }
            .map { (key, group) ->
                val ordered = group.sortedWith(compareBy({ it.startTimeSec }, { it.schedTripId }))
                val updates = ordered.mapIndexed { i, t ->
                    val next = ordered.getOrNull(i + 1)
                    BlockTripUpdate(
                        schedTripId = t.schedTripId,
                        listIndex = i,
                        layoverAfterSec = next?.let { it.startTimeSec - t.endTimeSec },
                        deadheadAfter = next?.let { it.firstStopId != t.lastStopId },
                    )
                }
                BlockResult(
                    blockId = key.first,
                    serviceId = key.second,
                    startTimeSec = ordered.first().startTimeSec,
                    endTimeSec = ordered.maxOf { it.endTimeSec },
                    tripCount = ordered.size,
                    routeIds = ordered.map { it.routeId }.distinct(),
                    tripUpdates = updates,
                )
            }
}
