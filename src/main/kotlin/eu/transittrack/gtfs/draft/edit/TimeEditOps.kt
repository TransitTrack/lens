package eu.transittrack.gtfs.draft.edit

import tools.jackson.databind.node.ObjectNode

private fun ObjectNode.putNullableInt(
    name: String,
    v: Int?,
): ObjectNode = if (v == null) putNull(name) else put(name, v)

private fun fmtClock(s: Int?): String = s?.let { "%d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60) } ?: "—"

/**
 * Set (or clear) the arrival/departure of one stop time. A `null` arg means "set to NULL";
 * a non-null arg means "set to that value". The forward/inverse JSON always carries both
 * `arr` and `dep` as either a number or JSON null, so the replay builder is a pure function
 * of the direction JSON.
 */
class UpdateStopTimeOp(
    private val tripId: String,
    private val stopSequence: Int,
    private val arrivalSec: Int?,
    private val departureSec: Int?,
) : EditOp {
    override val op = "UPDATE_STOP_TIME"

    override fun plan(ctx: EditContext): PlannedEdit {
        val row =
            ctx.stopTimes
                .findByTripId(ctx.revisionId, tripId)
                .single { it.stopSequence == stopSequence }
        val oldArr = row.arrivalTime
        val oldDep = row.departureTime
        val fwd =
            ctx.json
                .createObjectNode()
                .put("tripId", tripId)
                .put("seq", stopSequence)
                .putNullableInt("arr", arrivalSec)
                .putNullableInt("dep", departureSec)
        val inv =
            ctx.json
                .createObjectNode()
                .put("tripId", tripId)
                .put("seq", stopSequence)
                .putNullableInt("arr", oldArr)
                .putNullableInt("dep", oldDep)
        return PlannedEdit(
            "Set $tripId#$stopSequence to ${fmtClock(arrivalSec)}/${fmtClock(departureSec)}",
            fwd,
            inv,
            mutate = {
                row.arrivalTime = if (fwd.get("arr").isNull) null else fwd.get("arr").asInt()
                row.departureTime = if (fwd.get("dep").isNull) null else fwd.get("dep").asInt()
                ctx.stopTimes.save(row)
            },
        )
    }

    companion object {
        init {
            EditOpRegistry.register("UPDATE_STOP_TIME") { ctx, dir ->
                {
                    val row =
                        ctx.stopTimes
                            .findByTripId(ctx.revisionId, dir.get("tripId").asString())
                            .single { it.stopSequence == dir.get("seq").asInt() }
                    row.arrivalTime = if (dir.get("arr").isNull) null else dir.get("arr").asInt()
                    row.departureTime = if (dir.get("dep").isNull) null else dir.get("dep").asInt()
                    ctx.stopTimes.save(row)
                }
            }
        }
    }
}

/**
 * Shift every non-null arrival/departure of a trip by `deltaSec`. A negative delta is clamped so
 * no time drops below 0; the forward AND inverse JSON carry the *actual applied* delta so undo
 * lands exactly back on the original values.
 */
class ShiftTripOp(
    private val tripId: String,
    private val deltaSec: Int,
) : EditOp {
    override val op = "SHIFT_TRIP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val rows = ctx.stopTimes.findByTripId(ctx.revisionId, tripId)
        val minTime =
            rows
                .flatMap { listOfNotNull(it.arrivalTime, it.departureTime) }
                .minOrNull() ?: 0
        val applied = if (deltaSec < 0) maxOf(deltaSec, -minTime) else deltaSec
        val fwd = ctx.json
            .createObjectNode()
            .put("tripId", tripId)
            .put("delta", applied)
        val inv = ctx.json
            .createObjectNode()
            .put("tripId", tripId)
            .put("delta", -applied)
        return PlannedEdit(
            "Shift trip $tripId by ${if (applied >= 0) "+" else ""}${applied / 60}m",
            fwd,
            inv,
            mutate = { shift(ctx, tripId, applied) },
        )
    }

    companion object {
        internal fun shift(
            ctx: EditContext,
            tripId: String,
            delta: Int,
        ) {
            val rows = ctx.stopTimes.findByTripId(ctx.revisionId, tripId)
            rows.forEach { r ->
                r.arrivalTime = r.arrivalTime?.plus(delta)
                r.departureTime = r.departureTime?.plus(delta)
            }
            ctx.stopTimes.saveAll(rows)
        }

        init {
            EditOpRegistry.register("SHIFT_TRIP") { ctx, dir ->
                { shift(ctx, dir.get("tripId").asString(), dir.get("delta").asInt()) }
            }
        }
    }
}

/**
 * Force a stop's dwell: `departure = arrival + dwellSec` (arrival falls back to the old departure,
 * then 0). The inverse restores the previous departure, which may be null.
 */
class SetStopDwellOp(
    private val tripId: String,
    private val stopSequence: Int,
    private val dwellSec: Int,
) : EditOp {
    override val op = "SET_DWELL"

    override fun plan(ctx: EditContext): PlannedEdit {
        val row =
            ctx.stopTimes
                .findByTripId(ctx.revisionId, tripId)
                .single { it.stopSequence == stopSequence }
        val oldDep = row.departureTime
        val arr = row.arrivalTime ?: oldDep ?: 0
        val fwd =
            ctx.json
                .createObjectNode()
                .put("tripId", tripId)
                .put("seq", stopSequence)
                .put("dep", arr + dwellSec)
        val inv =
            ctx.json
                .createObjectNode()
                .put("tripId", tripId)
                .put("seq", stopSequence)
                .putNullableInt("dep", oldDep)
        return PlannedEdit(
            "Dwell ${dwellSec}s at $tripId#$stopSequence",
            fwd,
            inv,
            mutate = {
                row.departureTime = arr + dwellSec
                ctx.stopTimes.save(row)
            },
        )
    }

    companion object {
        init {
            EditOpRegistry.register("SET_DWELL") { ctx, dir ->
                {
                    val row =
                        ctx.stopTimes
                            .findByTripId(ctx.revisionId, dir.get("tripId").asString())
                            .single { it.stopSequence == dir.get("seq").asInt() }
                    row.departureTime = if (dir.get("dep").isNull) null else dir.get("dep").asInt()
                    ctx.stopTimes.save(row)
                }
            }
        }
    }
}
