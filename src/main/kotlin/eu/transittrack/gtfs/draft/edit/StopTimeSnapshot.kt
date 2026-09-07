package eu.transittrack.gtfs.draft.edit

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

import eu.transittrack.gtfs.model.StopTime

private fun JsonNode.stStr(field: String): String? = get(field)?.takeUnless { it.isNull }?.asString()

private fun JsonNode.stInt(field: String): Int? = get(field)?.takeUnless { it.isNull }?.asInt()

private fun JsonNode.stDbl(field: String): Double? = get(field)?.takeUnless { it.isNull }?.asDouble()

private fun ObjectNode.putStrN(
    field: String,
    v: String?,
): ObjectNode = if (v == null) putNull(field) else put(field, v)

private fun ObjectNode.putIntN(
    field: String,
    v: Int?,
): ObjectNode = if (v == null) putNull(field) else put(field, v)

private fun ObjectNode.putDblN(
    field: String,
    v: Double?,
): ObjectNode = if (v == null) putNull(field) else put(field, v)

/**
 * The single source of truth for serialising a [StopTime] to JSON and back. Every one of the 18
 * non-revision columns round-trips, so a "delete all + re-insert from snapshot" restore rebuilds the
 * rows byte-for-byte (only the surrogate ids change, re-issued on `save`).
 *
 * Reused by both the trip-lifecycle ops ([TripLifecycle]) and the stop-sequence ops
 * ([InsertTripStopOp] / [RemoveTripStopOp] / [ReorderTripStopsOp]).
 */
internal object StopTimeSnapshot {
    fun toNode(
        json: JsonMapper,
        s: StopTime,
    ): ObjectNode =
        json
            .createObjectNode()
            .put("tripId", s.tripId)
            .put("stopSequence", s.stopSequence)
            .putStrN("stopId", s.stopId)
            .putIntN("arrivalTime", s.arrivalTime)
            .putIntN("departureTime", s.departureTime)
            .putStrN("locationGroupId", s.locationGroupId)
            .putStrN("locationId", s.locationId)
            .putStrN("stopHeadsign", s.stopHeadsign)
            .putIntN("startPickupDropOffWindow", s.startPickupDropOffWindow)
            .putIntN("endPickupDropOffWindow", s.endPickupDropOffWindow)
            .putIntN("pickupType", s.pickupType)
            .putIntN("dropOffType", s.dropOffType)
            .putIntN("continuousPickup", s.continuousPickup)
            .putIntN("continuousDropOff", s.continuousDropOff)
            .putDblN("shapeDistTraveled", s.shapeDistTraveled)
            .putIntN("timepoint", s.timepoint)
            .putStrN("pickupBookingRuleId", s.pickupBookingRuleId)
            .putStrN("dropOffBookingRuleId", s.dropOffBookingRuleId)

    /** Build a fresh (unsaved) [StopTime] from a node, under `ctx.revisionId`, with `stopSequence` overridable. */
    fun fromNode(
        ctx: EditContext,
        node: JsonNode,
        stopSequence: Int = node.get("stopSequence").asInt(),
    ): StopTime =
        StopTime(
            ctx.revisionId,
            tripId = node.get("tripId").asString(),
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
    ): ArrayNode {
        val arr = ctx.json.createArrayNode()
        ctx.stopTimes.findByTripId(ctx.revisionId, tripId).forEach { arr.add(toNode(ctx.json, it)) }
        return arr
    }

    /** Delete every stop time of `tripId` and re-insert one fresh row per node in `rows`. */
    fun restore(
        ctx: EditContext,
        tripId: String,
        rows: JsonNode,
    ) {
        ctx.stopTimes.findByTripId(ctx.revisionId, tripId).let(ctx.stopTimes::deleteAll)
        ctx.stopTimes.flush()
        rows.forEach { node -> ctx.stopTimes.save(fromNode(ctx, node)) }
    }

    /** Register `op`'s undo/redo builder: replay the target snapshot held under `rows`. */
    fun registerRestoreBuilder(op: String) {
        EditOpRegistry.register(op) { ctx, dir ->
            { restore(ctx, dir.get("tripId").asString(), dir.get("rows")) }
        }
    }
}
