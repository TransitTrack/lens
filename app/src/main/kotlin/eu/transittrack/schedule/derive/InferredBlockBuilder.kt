package eu.transittrack.schedule.derive

import eu.transittrack.ScheduleProperties
import eu.transittrack.gtfs.model.RouteTypes
import eu.transittrack.haversineMeters

/** A timed trip that may be assigned to an inferred vehicle block. */
data class InferredBlockTripInput(
    val tripRowId: Long,
    val tripId: String,
    val serviceId: String,
    val routeId: String,
    val patternId: Long,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val firstStopId: String,
    val lastStopId: String,
    /** GTFS `route_type`; a vehicle only moves between routes of the same basic type. `null` when unknown. */
    val routeType: Int? = null,
)

/** Location facts used to decide whether two stop ids are the same terminal. */
data class InferredBlockStop(
    val parentStation: String?,
    val lat: Double?,
    val lon: Double?,
)

/** Builds conservative vehicle-work chains when a GTFS feed omits `trips.block_id`. */
object InferredBlockBuilder {
    fun build(
        trips: List<InferredBlockTripInput>,
        options: ScheduleProperties.InferredBlocks,
        stops: Map<String, InferredBlockStop> = emptyMap(),
    ): List<BlockResult> {
        require(options.maxDeadheadGapSec >= 0) { "inferredBlocks.maxDeadheadGapSec must be non-negative" }
        require(options.maxLayoverSec >= 0) { "inferredBlocks.maxLayoverSec must be non-negative" }
        require(options.sameTerminalRadiusM >= 0) { "inferredBlocks.sameTerminalRadiusM must be non-negative" }
        require(options.deadheadSpeedMps > 0) { "inferredBlocks.deadheadSpeedMps must be positive" }

        val linker = Linker(options, stops)
        return trips
            .groupBy { it.serviceId }
            .flatMap { (serviceId, serviceTrips) -> buildServiceChains(serviceId, serviceTrips, linker) }
    }

    /**
     * Two-phase chain building. Phase 1 chains each route's own trips (both direction patterns) in
     * isolation, so a vehicle's own route is always exhausted before we reach for a different route
     * to explain a gap. Phase 2 treats every phase-1 chain, single trips included, as one unit and
     * joins a chain's last trip onto another chain's first trip. That recovers interlining, e.g. a
     * vehicle working R1 in the morning and R2 from the same terminal in the afternoon.
     */
    private fun buildServiceChains(
        serviceId: String,
        trips: List<InferredBlockTripInput>,
        linker: Linker,
    ): List<BlockResult> {
        val routeChains =
            trips
                .groupBy { it.routeId }
                .values
                .flatMap { routeTrips -> linkUnits(routeTrips.map { listOf(it) }, linker) }
        return linkUnits(routeChains, linker).map { toBlockResult(serviceId, it, linker) }
    }

    /**
     * Greedily concatenates [units] (each an already-ordered run of trips) into chains: starting from
     * the earliest unassigned unit, keep appending [Linker.bestSuccessor] until none is compatible.
     */
    private fun linkUnits(
        units: List<List<InferredBlockTripInput>>,
        linker: Linker,
    ): List<List<InferredBlockTripInput>> {
        val unassigned =
            units
                .sortedWith(compareBy({ it.first().startTimeSec }, { it.first().tripRowId }))
                .toMutableList()
        val chains = ArrayList<List<InferredBlockTripInput>>()
        while (unassigned.isNotEmpty()) {
            val chain = ArrayList(unassigned.removeAt(0))
            while (true) {
                val successorIndex = linker.bestSuccessor(chain.last(), unassigned) ?: break
                chain.addAll(unassigned.removeAt(successorIndex))
            }
            chains.add(chain)
        }
        return chains
    }

    private fun toBlockResult(
        serviceId: String,
        chain: List<InferredBlockTripInput>,
        linker: Linker,
    ): BlockResult {
        // Each trip lands in exactly one chain, so the first trip id keeps the block id unique within
        // the service and stable across re-ingests of the same feed.
        val blockId = "inferred:$serviceId:${chain.first().tripId}"
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
                // A platform change inside one terminal is not a deadhead.
                isDeadhead = { from, to -> !linker.sameTerminal(from, to) },
            ).single()
    }
}

private enum class Link { SAME_TERMINAL, DEADHEAD }

/**
 * Decides whether one trip can follow another on the same vehicle. A wrong link sends AVL matching
 * and predictions onto another vehicle's trip, while a missing one only loses block continuity, so
 * every rule errs towards not linking.
 */
private class Linker(
    private val options: ScheduleProperties.InferredBlocks,
    private val stops: Map<String, InferredBlockStop>,
) {
    private val horizonSec =
        maxOf(options.maxLayoverSec, if (options.allowDeadhead) options.maxDeadheadGapSec else 0)

    /**
     * Index of the best successor to [current] in [candidates] (sorted by first start time), or
     * `null`. A same-terminal continuation beats a deadhead; within each kind the earliest start
     * (shortest wait) wins.
     */
    fun bestSuccessor(
        current: InferredBlockTripInput,
        candidates: List<List<InferredBlockTripInput>>,
    ): Int? {
        var firstDeadhead: Int? = null
        for ((index, unit) in candidates.withIndex()) {
            val gapSec = unit.first().startTimeSec - current.endTimeSec
            if (gapSec > horizonSec) break
            when (link(current, unit.first(), gapSec)) {
                Link.SAME_TERMINAL -> {
                    return index
                }

                Link.DEADHEAD -> {
                    if (firstDeadhead == null) firstDeadhead = index
                }

                null -> {}
            }
        }
        return firstDeadhead
    }

    private fun link(
        current: InferredBlockTripInput,
        candidate: InferredBlockTripInput,
        gapSec: Int,
    ): Link? {
        if (gapSec < 0) {
            return null
        }
        if (current.routeId != candidate.routeId && !sameVehicleKind(current.routeType, candidate.routeType)) {
            return null
        }
        if (sameTerminal(current.lastStopId, candidate.firstStopId)) {
            return if (gapSec <= options.maxLayoverSec) Link.SAME_TERMINAL else null
        }
        if (!options.allowDeadhead || gapSec > options.maxDeadheadGapSec) {
            return null
        }
        // Without coordinates the deadhead can't be shown to be drivable in the gap.
        val distanceM = distanceM(stops[current.lastStopId], stops[candidate.firstStopId]) ?: return null
        return if (gapSec >= distanceM / options.deadheadSpeedMps) Link.DEADHEAD else null
    }

    /**
     * A bus can't carry on as a tram: switching routes needs known route types of the same basic
     * kind, so an extended `704` (local bus) still matches a basic `3` (bus).
     */
    private fun sameVehicleKind(
        from: Int?,
        to: Int?,
    ): Boolean = from != null && to != null && RouteTypes.basic(from) == RouteTypes.basic(to)

    fun sameTerminal(
        fromStopId: String,
        toStopId: String,
    ): Boolean {
        if (fromStopId == toStopId) {
            return true
        }
        val from = stops[fromStopId] ?: return false
        val to = stops[toStopId] ?: return false
        if (from.parentStation != null && from.parentStation == to.parentStation) {
            return true
        }
        val distanceM = distanceM(from, to) ?: return false
        return distanceM <= options.sameTerminalRadiusM
    }

    private fun distanceM(
        from: InferredBlockStop?,
        to: InferredBlockStop?,
    ): Double? {
        val fromLat = from?.lat ?: return null
        val fromLon = from.lon ?: return null
        val toLat = to?.lat ?: return null
        val toLon = to.lon ?: return null
        return haversineMeters(fromLat, fromLon, toLat, toLon)
    }
}
