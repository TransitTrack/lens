package eu.transittrack.schedule.derive

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

import eu.transittrack.ScheduleProperties
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.median
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockTrip

/**
 * Stage 4 of schedule derivation. Builds `block` + `block_trip` and the per-pattern layover
 * write-back onto `stop_path`.
 *
 * Three kinds of block are produced from `state.derivedTrips`:
 *  1. Scheduled blocks — a port of the former `ScheduleDerivationService.blockPass`: ordinary
 *     `block_id` trips reconstructed into ordered vehicle runs by [BlockBuilder].
 *  2. Frequency blocks — one synthetic block per frequency-based trip that carries a `block_id`,
 *     spanning the `frequencies` row's start/end.
 *  3. Unscheduled blocks — only when [ScheduleProperties.tolerateNoScheduleTrips]: one block per
 *     `(block_id, service_id)` of no-schedule trips, spanning the whole service day.
 */
@Component
@Order(BlockProcessor.ORDER)
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class BlockProcessor(
    private val context: DerivationContext,
    private val writer: ScheduleWriter,
    private val props: ScheduleProperties,
    private val frequencies: FrequencyRepository,
    private val stops: StopRepository,
) : IngestionPostProcessor {
    companion object {
        const val ORDER = 40
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        val state = context.get(revisionId)

        var totalBlocks = 0L
        var totalBlockTrips = 0L

        // --- 1. Scheduled blocks (port of blockPass) ---------------------------------------
        val providedBlockInputs =
            state.derivedTrips
                .filter { it.blockId != null && !it.frequencyBased && !it.noSchedule }
                .map {
                    BlockTripInput(
                        tripRowId = it.tripRowId,
                        blockId = it.blockId!!,
                        serviceId = it.serviceId,
                        routeId = it.routeId,
                        startTimeSec = it.startSec,
                        endTimeSec = it.endSec,
                        firstStopId = it.firstStopId,
                        lastStopId = it.lastStopId,
                    )
                }
        val inferredBlockInputs =
            state.derivedTrips
                .filter { it.blockId == null && !it.frequencyBased && !it.noSchedule }
                .map {
                    InferredBlockTripInput(
                        tripRowId = it.tripRowId,
                        tripId = it.tripId,
                        serviceId = it.serviceId,
                        routeId = it.routeId,
                        patternId = it.patternId,
                        startTimeSec = it.startSec,
                        endTimeSec = it.endSec,
                        firstStopId = it.firstStopId,
                        lastStopId = it.lastStopId,
                        routeType = it.routeType,
                    )
                }
        val inferredResults =
            if (props.inferredBlocks.enabled && inferredBlockInputs.isNotEmpty()) {
                val stopsById =
                    stops.findByRevisionId(revisionId).associate {
                        it.stopId to InferredBlockStop(it.parentStation, it.stopLat, it.stopLon)
                    }
                InferredBlockBuilder.build(inferredBlockInputs, props.inferredBlocks, stopsById)
            } else {
                inferredBlockInputs.map { InferredBlockBuilder.build(listOf(it), props.inferredBlocks).single() }
            }
        val results = BlockBuilder.build(providedBlockInputs) + inferredResults
        val blockRows =
            results.map {
                Block(
                    revisionId = revisionId,
                    blockId = it.blockId,
                    serviceId = it.serviceId,
                    startTimeSec = it.startTimeSec,
                    endTimeSec = it.endTimeSec,
                    tripCount = it.tripCount,
                    routeIds = it.routeIds,
                )
            }
        writer.write(blockRows)
        totalBlocks += blockRows.size

        val blockPkByKey = blockRows.associate { (it.blockId to it.serviceId) to it.id!! }
        val blockTripRows =
            results.flatMap { r ->
                val blockPk = blockPkByKey.getValue(r.blockId to r.serviceId)
                r.tripUpdates.map { u ->
                    BlockTrip(
                        revisionId = revisionId,
                        blockId = blockPk,
                        tripId = u.tripRowId,
                        listIndex = u.listIndex,
                        layoverAfterSec = u.layoverAfterSec,
                        deadheadAfter = u.deadheadAfter,
                    )
                }
            }
        writer.write(blockTripRows)
        totalBlockTrips += blockTripRows.size

        // Per-pattern layover flag on the last stop path.
        val layoverByTrip =
            results
                .flatMap { it.tripUpdates }
                .filter { (it.layoverAfterSec ?: -1) >= props.layoverThresholdSec }
                .associate { it.tripRowId to it.layoverAfterSec!! }
        val layoverUpdates = ArrayList<StopPathLayoverUpdate>()
        for ((patternId, tripsOfPattern) in state.derivedTrips.groupBy { it.patternId }) {
            val gaps = tripsOfPattern.mapNotNull { layoverByTrip[it.tripRowId] }
            if (gaps.isEmpty()) continue
            val pathIds = state.patternStopPathIds[patternId] ?: continue
            layoverUpdates.add(
                StopPathLayoverUpdate(
                    stopPathId = pathIds.last(),
                    layoverStop = true,
                    breakTimeSec = median(gaps),
                ),
            )
        }
        writer.applyStopPathLayover(layoverUpdates)

        // --- 2. Frequency blocks ----------------------------------------------------------
        val freqByTrip =
            frequencies.findByRevisionId(revisionId).groupBy { it.tripId }.mapValues { it.value.first() }
        val freqBlockRows = ArrayList<Block>()
        val freqBlockTrips = ArrayList<Pair<String, DerivedTrip>>() // block_id value -> trip
        for (dt in state.derivedTrips.filter { it.frequencyBased }) {
            val f = freqByTrip[dt.tripId] ?: continue
            val sourceBlockId = dt.blockId ?: "inferred:${dt.serviceId}:${dt.tripId}"
            val blockIdValue = "$sourceBlockId|${dt.tripId}"
            freqBlockRows.add(
                Block(
                    revisionId = revisionId,
                    blockId = blockIdValue,
                    serviceId = dt.serviceId,
                    startTimeSec = f.startTime,
                    endTimeSec = f.endTime ?: 86_400,
                    tripCount = 1,
                    routeIds = listOf(dt.routeId),
                ),
            )
            freqBlockTrips.add(blockIdValue to dt)
        }
        writer.write(freqBlockRows)
        totalBlocks += freqBlockRows.size
        val freqPk = freqBlockRows.associate { (it.blockId to it.serviceId) to it.id!! }
        val freqBlockTripRows =
            freqBlockTrips.map { (bid, dt) ->
                BlockTrip(
                    revisionId = revisionId,
                    blockId = freqPk.getValue(bid to dt.serviceId),
                    tripId = dt.tripRowId,
                    listIndex = 0,
                    layoverAfterSec = null,
                    deadheadAfter = null,
                )
            }
        writer.write(freqBlockTripRows)
        totalBlockTrips += freqBlockTripRows.size

        // --- 3. Unscheduled blocks ------------------------------------------------------
        // NOTE (accepted limitation): with `tolerateNoScheduleTrips = true`, if a single
        // `block_id` has BOTH timed trips and timeless (no-schedule) trips under the same
        // service, this branch emits an unscheduled `Block` while section 1 emits a scheduled
        // `Block` for the same `(revision_id, block_id, service_id)`. That collides with
        // `uq_block_rev_block_service` and fails the ingest. Merging/skipping such mixed
        // blocks is intentionally not implemented here.
        if (props.tolerateNoScheduleTrips) {
            val groups =
                state.derivedTrips
                    .filter { it.noSchedule }
                    .groupBy { (it.blockId ?: "inferred:${it.serviceId}:${it.tripId}") to it.serviceId }
            val unschedBlockRows = ArrayList<Block>()
            val unschedBlockTripInputs = ArrayList<Pair<Pair<String, String>, List<DerivedTrip>>>()
            for ((key, group) in groups) {
                val ordered = group.sortedBy { it.tripId }
                unschedBlockRows.add(
                    Block(
                        revisionId = revisionId,
                        blockId = key.first,
                        serviceId = key.second,
                        startTimeSec = 0,
                        endTimeSec = 86_400,
                        tripCount = ordered.size,
                        routeIds = ordered.map { it.routeId }.distinct(),
                    ),
                )
                unschedBlockTripInputs.add(key to ordered)
            }
            writer.write(unschedBlockRows)
            totalBlocks += unschedBlockRows.size
            val unschedPk = unschedBlockRows.associate { (it.blockId to it.serviceId) to it.id!! }
            val unschedBlockTripRows =
                unschedBlockTripInputs.flatMap { (key, ordered) ->
                    val blockPk = unschedPk.getValue(key)
                    ordered.mapIndexed { i, t ->
                        val next = ordered.getOrNull(i + 1)
                        BlockTrip(
                            revisionId = revisionId,
                            blockId = blockPk,
                            tripId = t.tripRowId,
                            listIndex = i,
                            // Both trips are synthesised with startSec=0/endSec=86_400, so a
                            // real layover gap is meaningless between two no-schedule trips.
                            layoverAfterSec =
                                next?.let {
                                    if (t.noSchedule && it.noSchedule) null else it.startSec - t.endSec
                                },
                            deadheadAfter = next?.let { it.firstStopId != t.lastStopId },
                        )
                    }
                }
            writer.write(unschedBlockTripRows)
            totalBlockTrips += unschedBlockTripRows.size
        }

        return mapOf("block" to totalBlocks, "block_trip" to totalBlockTrips)
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { writer.deleteTables(revisionId, "block_trip", "block") }
        runCatching { writer.resetStopPathLayover(revisionId) }
        runCatching { context.close(revisionId) }
    }
}
