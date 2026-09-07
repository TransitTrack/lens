package eu.transittrack.gtfs.draft.edit

import tools.jackson.databind.node.ArrayNode

import eu.transittrack.gtfs.model.StopTime

/**
 * Shared machinery for the three stop-sequence ops. Each op computes the whole post-change ordered
 * list of a trip's stop times in memory, then journals:
 *
 * - `forward` = `{ tripId, rows: [<full-column node>] }` with `stop_sequence` renumbered densely 1..n;
 * - `inverse` = the verbatim pre-change whole-trip snapshot (original `stop_sequence` values kept).
 *
 * Applying, undoing and redoing are all the same operation: delete every stop time of the trip and
 * re-insert one fresh row per node in the target `rows` array ([StopTimeSnapshot.restore]).
 */
private object StopSeq {
    /** Serialise an ordered list to a `rows` array, renumbering `stop_sequence` densely 1..n. */
    fun denseRows(
        ctx: EditContext,
        ordered: List<StopTime>,
    ): ArrayNode {
        val arr = ctx.json.createArrayNode()
        ordered.forEachIndexed { i, st -> arr.add(StopTimeSnapshot.toNode(ctx.json, st).put("stopSequence", i + 1)) }
        return arr
    }

    fun plannedFor(
        ctx: EditContext,
        tripId: String,
        summary: String,
        ordered: List<StopTime>,
    ): PlannedEdit {
        val forwardRows = denseRows(ctx, ordered)
        val fwd = ctx.json.createObjectNode().put("tripId", tripId)
        fwd.replace("rows", forwardRows)
        val inv = ctx.json.createObjectNode().put("tripId", tripId)
        inv.replace("rows", StopTimeSnapshot.snapshotRows(ctx, tripId))
        return PlannedEdit(
            summary,
            fwd,
            inv,
            mutate = { StopTimeSnapshot.restore(ctx, tripId, forwardRows) },
        )
    }

    fun currentRows(
        ctx: EditContext,
        tripId: String,
    ): List<StopTime> =
        ctx.stopTimes
            .findByTripId(ctx.revisionId, tripId)
            .sortedBy { it.stopSequence }
            .also { require(it.isNotEmpty()) { "no stop_times for trip $tripId" } }
}

/**
 * Insert a new stop after `afterStopSequence` (0 = at the head of the trip). When `arrivalSec` /
 * `departureSec` are null the arrival is the midpoint between the previous stop's departure (falling
 * back to its arrival) and the next stop's arrival (falling back to its departure); `departure =
 * arrival`. At the head or tail (only one neighbour) the time is that neighbour's time -/+ 60s; with
 * no neighbour at all it is 0. All other 13 columns of the new row are null. Every `stop_sequence`
 * is then renumbered densely 1..n.
 */
class InsertTripStopOp(
    private val tripId: String,
    private val afterStopSequence: Int,
    private val stopId: String,
    private val arrivalSec: Int?,
    private val departureSec: Int?,
) : EditOp {
    override val op = "INSERT_TRIP_STOP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val current = StopSeq.currentRows(ctx, tripId)
        val idx =
            if (afterStopSequence == 0) {
                0
            } else {
                val at = current.indexOfFirst { it.stopSequence == afterStopSequence }
                require(at >= 0) { "trip $tripId has no stop_sequence $afterStopSequence" }
                at + 1
            }
        val prev = current.getOrNull(idx - 1)
        val next = current.getOrNull(idx)
        val prevT = prev?.let { it.departureTime ?: it.arrivalTime }
        val nextT = next?.let { it.arrivalTime ?: it.departureTime }
        val arrival =
            arrivalSec ?: when {
                prevT != null && nextT != null -> (prevT + nextT) / 2
                prevT != null -> prevT + 60
                nextT != null -> nextT - 60
                else -> 0
            }
        val departure = departureSec ?: arrival
        val newRow =
            StopTime(
                ctx.revisionId, tripId, 0, stopId, arrival, departure,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
            )
        val ordered = current.toMutableList().apply { add(idx, newRow) }
        return StopSeq.plannedFor(ctx, tripId, "Insert stop $stopId into $tripId (after #$afterStopSequence)", ordered)
    }

    companion object {
        init {
            StopTimeSnapshot.registerRestoreBuilder("INSERT_TRIP_STOP")
        }
    }
}

/** Remove the stop at `stopSequence` and renumber the rest densely 1..n. */
class RemoveTripStopOp(
    private val tripId: String,
    private val stopSequence: Int,
) : EditOp {
    override val op = "REMOVE_TRIP_STOP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val current = StopSeq.currentRows(ctx, tripId)
        require(current.any { it.stopSequence == stopSequence }) {
            "trip $tripId has no stop_sequence $stopSequence"
        }
        val ordered = current.filterNot { it.stopSequence == stopSequence }
        return StopSeq.plannedFor(ctx, tripId, "Remove stop #$stopSequence from $tripId", ordered)
    }

    companion object {
        init {
            StopTimeSnapshot.registerRestoreBuilder("REMOVE_TRIP_STOP")
        }
    }
}

/**
 * Reorder a trip's stops so their `stopId`s follow `stopIdOrder`. Each existing row moves as a unit
 * (all 18 columns, times included) to its new position; `stop_sequence` is renumbered densely 1..n.
 * Rejects a trip that visits the same `stopId` more than once (ambiguous) and a `stopIdOrder` that is
 * not an exact permutation of the trip's current stop ids.
 */
class ReorderTripStopsOp(
    private val tripId: String,
    private val stopIdOrder: List<String>,
) : EditOp {
    override val op = "REORDER_TRIP_STOPS"

    override fun plan(ctx: EditContext): PlannedEdit {
        val current = StopSeq.currentRows(ctx, tripId)
        require(current.all { it.stopId != null }) {
            "cannot reorder a trip with unnamed stops by stop id"
        }
        val currentIds = current.mapNotNull { it.stopId }
        require(currentIds.toSet().size == currentIds.size) {
            "cannot reorder a trip with duplicate stops by stop id"
        }
        require(stopIdOrder.size == current.size && stopIdOrder.sorted() == currentIds.sorted()) {
            "stopIdOrder must be a permutation of the trip's current stop ids"
        }
        val byId = current.associateBy { it.stopId }
        val ordered = stopIdOrder.map { byId.getValue(it) }
        return StopSeq.plannedFor(ctx, tripId, "Reorder stops of $tripId", ordered)
    }

    companion object {
        init {
            StopTimeSnapshot.registerRestoreBuilder("REORDER_TRIP_STOPS")
        }
    }
}
