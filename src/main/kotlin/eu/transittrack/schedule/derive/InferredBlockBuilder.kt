package eu.transittrack.schedule.derive

import eu.transittrack.ScheduleProperties

/** A timed trip that may be assigned to an inferred vehicle block. */
data class InferredBlockTripInput(
    val tripRowId: Long,
    val tripId: String,
    val serviceId: String,
    val routeId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val firstStopId: String,
    val lastStopId: String,
)

/** Builds conservative vehicle-work chains when a GTFS feed omits `trips.block_id`. */
object InferredBlockBuilder {
    fun build(
        trips: List<InferredBlockTripInput>,
        options: ScheduleProperties.InferredBlocks,
    ): List<BlockResult> {
        require(options.maxDeadheadGapSec >= 0) { "inferredBlocks.maxDeadheadGapSec must be non-negative" }

        return trips
            .groupBy { it.serviceId }
            .toSortedMap()
            .flatMap { (_, serviceTrips) -> buildServiceChains(serviceTrips, options) }
    }

    private fun buildServiceChains(
        trips: List<InferredBlockTripInput>,
        options: ScheduleProperties.InferredBlocks,
    ): List<BlockResult> {
        val unassigned = trips.sortedWith(compareBy({ it.startTimeSec }, { it.tripRowId })).toMutableList()
        val results = ArrayList<BlockResult>()

        while (unassigned.isNotEmpty()) {
            val chain = ArrayList<InferredBlockTripInput>()
            var current = unassigned.removeAt(0)
            chain.add(current)

            while (true) {
                val successorIndex = unassigned.indexOfFirst { candidate -> compatibleSuccessor(current, candidate, options) }
                if (successorIndex < 0) break
                current = unassigned.removeAt(successorIndex)
                chain.add(current)
            }

            results.add(toBlockResult(chain))
        }

        return results
    }

    private fun compatibleSuccessor(
        current: InferredBlockTripInput,
        candidate: InferredBlockTripInput,
        options: ScheduleProperties.InferredBlocks,
    ): Boolean {
        val gapSec = candidate.startTimeSec - current.endTimeSec
        if (gapSec < 0) return false
        if (current.lastStopId == candidate.firstStopId) return true
        return options.allowDeadhead && gapSec <= options.maxDeadheadGapSec
    }

    private fun toBlockResult(chain: List<InferredBlockTripInput>): BlockResult {
        val first = chain.first()
        val blockId = "inferred:${first.serviceId}:${first.tripId}"
        return BlockBuilder
            .build(
                chain.map {
                    BlockTripInput(
                        tripRowId = it.tripRowId,
                        blockId = blockId,
                        serviceId = it.serviceId,
                        routeId = it.routeId,
                        startTimeSec = it.startTimeSec,
                        endTimeSec = it.endTimeSec,
                        firstStopId = it.firstStopId,
                        lastStopId = it.lastStopId,
                    )
                },
            ).single()
    }
}
