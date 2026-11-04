package eu.transittrack.gtfs.draft.edit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import eu.transittrack.gtfs.model.StopTime

private fun JsonObject.stStr(field: String): String? = get(field)?.jsonPrimitive?.contentOrNull

private fun JsonObject.stInt(field: String): Int? = get(field)?.takeUnless { it == JsonNull }?.jsonPrimitive?.int

private fun JsonObject.stDbl(field: String): Double? =
    get(field)?.takeUnless { it == JsonNull }?.jsonPrimitive?.double?.also {
        require(it.isFinite()) { "$field must be a finite double" }
    }

/**
 * The single source of truth for serialising a [StopTime] to JSON and back. Every one of the 18
 * non-revision columns round-trips, so a "delete all + re-insert from snapshot" restore rebuilds the
 * rows byte-for-byte (only the surrogate ids change, re-issued on `save`).
 *
 * Reused by both the trip-lifecycle ops ([TripLifecycle]) and the stop-sequence ops
 * ([InsertTripStopOp] / [RemoveTripStopOp] / [ReorderTripStopsOp]).
 */
internal object StopTimeSnapshot {
    fun toNode(s: StopTime): JsonObject =
        buildJsonObject {
            put("tripId", s.tripId)
            put("stopSequence", s.stopSequence)
            put("stopId", s.stopId)
            put("arrivalTime", s.arrivalTime)
            put("departureTime", s.departureTime)
            put("locationGroupId", s.locationGroupId)
            put("locationId", s.locationId)
            put("stopHeadsign", s.stopHeadsign)
            put("startPickupDropOffWindow", s.startPickupDropOffWindow)
            put("endPickupDropOffWindow", s.endPickupDropOffWindow)
            put("pickupType", s.pickupType)
            put("dropOffType", s.dropOffType)
            put("continuousPickup", s.continuousPickup)
            put("continuousDropOff", s.continuousDropOff)
            put("shapeDistTraveled", s.shapeDistTraveled)
            put("timepoint", s.timepoint)
            put("pickupBookingRuleId", s.pickupBookingRuleId)
            put("dropOffBookingRuleId", s.dropOffBookingRuleId)
        }

    /** Build a fresh (unsaved) [StopTime] from a node, under `ctx.revisionId`, with `stopSequence` overridable. */
    fun fromNode(
        ctx: EditContext,
        node: JsonObject,
        stopSequence: Int = node.getValue("stopSequence").jsonPrimitive.int,
    ): StopTime =
        StopTime(
            ctx.revisionId,
            tripId = node.getValue("tripId").jsonPrimitive.content,
            stopSequence = stopSequence,
            stopId = node.stStr("stopId"),
            arrivalTime = node.stInt("arrivalTime"),
            departureTime = node.stInt("departureTime"),
            locationGroupId = node.stStr("locationGroupId"),
            locationId = node.stStr("locationId"),
            stopHeadsign = node.stStr("stopHeadsign"),
            startPickupDropOffWindow = node.stInt("startPickupDropOffWindow"),
            endPickupDropOffWindow = node.stInt("endPickupDropOffWindow"),
            pickupType = node.stInt("pickupType"),
            dropOffType = node.stInt("dropOffType"),
            continuousPickup = node.stInt("continuousPickup"),
            continuousDropOff = node.stInt("continuousDropOff"),
            shapeDistTraveled = node.stDbl("shapeDistTraveled"),
            timepoint = node.stInt("timepoint"),
            pickupBookingRuleId = node.stStr("pickupBookingRuleId"),
            dropOffBookingRuleId = node.stStr("dropOffBookingRuleId"),
        )

    /** Verbatim snapshot (original `stop_sequence` values preserved) of every stop time of a trip. */
    fun snapshotRows(
        ctx: EditContext,
        tripId: String,
    ): JsonArray = JsonArray(ctx.stopTimes.findByTripId(ctx.revisionId, tripId).map(::toNode))

    /** Delete every stop time of `tripId` and re-insert one fresh row per node in `rows`. */
    fun restore(
        ctx: EditContext,
        tripId: String,
        rows: JsonElement,
    ) {
        val restored = rows.jsonArray.map { node -> fromNode(ctx, node.jsonObject) }
        ctx.stopTimes.findByTripId(ctx.revisionId, tripId).let(ctx.stopTimes::deleteAll)
        restored.forEach { ctx.stopTimes.save(it) }
    }

    /** Register `op`'s undo/redo builder: replay the target snapshot held under `rows`. */
    fun registerRestoreBuilder(op: String) {
        EditOpRegistry.register(op) { ctx, dir ->
            {
                restore(
                    ctx,
                    dir.jsonObject
                        .getValue("tripId")
                        .jsonPrimitive.content,
                    dir.jsonObject.getValue("rows"),
                )
            }
        }
    }
}
