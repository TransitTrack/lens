package eu.transittrack.gtfs.draft.edit

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun fmtClock(s: Int?): String = s?.let { "%d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60) } ?: "—"

private fun JsonElement.nullableTime(): Int? = if (this == JsonNull) null else jsonPrimitive.int

/**
 * Set (or clear) the arrival/departure of one stop time. A `null` arg means "set to NULL";
 * a non-null arg means "set to that value". The forward/inverse JSON always carries both
 * `arr` and `dep` as either a number or JSON null, so the replay builder is a pure function
 * of the direction JSON.
 *
 * NOTE for the GraphQL layer (Task 6): a `null` arg means "set the column to NULL", NOT "leave
 * unchanged" — this op has no partial-update semantics.
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
            buildJsonObject {
                put("tripId", tripId)
                put("seq", stopSequence)
                put("arr", arrivalSec)
                put("dep", departureSec)
            }
        val inv =
            buildJsonObject {
                put("tripId", tripId)
                put("seq", stopSequence)
                put("arr", oldArr)
                put("dep", oldDep)
            }
        return PlannedEdit(
            "Set $tripId#$stopSequence to ${fmtClock(arrivalSec)}/${fmtClock(departureSec)}",
            fwd,
            inv,
            mutate = {
                row.arrivalTime = arrivalSec
                row.departureTime = departureSec
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
                            .findByTripId(
                                ctx.revisionId,
                                dir.jsonObject
                                    .getValue("tripId")
                                    .jsonPrimitive.content,
                            ).single {
                                it.stopSequence == dir.jsonObject
                                    .getValue("seq")
                                    .jsonPrimitive.int
                            }
                    val arrival = dir.jsonObject.getValue("arr").nullableTime()
                    val departure = dir.jsonObject.getValue("dep").nullableTime()
                    row.arrivalTime = arrival
                    row.departureTime = departure
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
        val fwd = buildJsonObject {
            put("tripId", tripId)
            put("delta", applied)
        }
        val inv = buildJsonObject {
            put("tripId", tripId)
            put("delta", -applied)
        }
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
                {
                    shift(
                        ctx,
                        dir.jsonObject
                            .getValue("tripId")
                            .jsonPrimitive.content,
                        dir.jsonObject
                            .getValue("delta")
                            .jsonPrimitive.int,
                    )
                }
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
            buildJsonObject {
                put("tripId", tripId)
                put("seq", stopSequence)
                put("dep", arr + dwellSec)
            }
        val inv =
            buildJsonObject {
                put("tripId", tripId)
                put("seq", stopSequence)
                put("dep", oldDep)
            }
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
                            .findByTripId(
                                ctx.revisionId,
                                dir.jsonObject
                                    .getValue("tripId")
                                    .jsonPrimitive.content,
                            ).single {
                                it.stopSequence == dir.jsonObject
                                    .getValue("seq")
                                    .jsonPrimitive.int
                            }
                    row.departureTime = dir.jsonObject.getValue("dep").nullableTime()
                    ctx.stopTimes.save(row)
                }
            }
        }
    }
}
