package eu.transittrack.gtfs.draft.edit

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Trip

/** One stop of a brand-new trip built by [AddTripOp]; both times are required. */
data class NewStopTime(
    val stopId: String,
    val arrivalSec: Int,
    val departureSec: Int,
)

private fun JsonNode.strOrNull(field: String): String? = get(field)?.takeUnless { it.isNull }?.asString()

private fun JsonNode.intOrNull(field: String): Int? = get(field)?.takeUnless { it.isNull }?.asInt()

private fun JsonNode.dblOrNull(field: String): Double? = get(field)?.takeUnless { it.isNull }?.asDouble()

private fun ObjectNode.putStr(
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
 * Shared machinery for the trip-lifecycle ops. A "full snapshot" JSON node is
 * `{ "trip": <all 10 non-derived Trip columns>, "stopTimes": [<all 18 non-revision StopTime columns>] }`
 * and round-trips every column so a delete -> undo (or an undo -> redo) restores rows byte-for-byte
 * (only the surrogate ids change, re-issued by the sequence on `save`).
 */
internal object TripLifecycle {
    fun tripNode(
        json: JsonMapper,
        t: Trip,
    ): ObjectNode =
        json
            .createObjectNode()
            .put("routeId", t.routeId)
            .put("serviceId", t.serviceId)
            .put("tripId", t.tripId)
            .putStr("tripHeadsign", t.tripHeadsign)
            .putStr("tripShortName", t.tripShortName)
            .putIntN("directionId", t.directionId)
            .putStr("blockId", t.blockId)
            .putStr("shapeId", t.shapeId)
            .putIntN("wheelchairAccessible", t.wheelchairAccessible)
            .putIntN("bikesAllowed", t.bikesAllowed)

    fun stopTimeNode(
        json: JsonMapper,
        s: StopTime,
    ): ObjectNode =
        json
            .createObjectNode()
            .put("tripId", s.tripId)
            .put("stopSequence", s.stopSequence)
            .putStr("stopId", s.stopId)
            .putIntN("arrivalTime", s.arrivalTime)
            .putIntN("departureTime", s.departureTime)
            .putStr("locationGroupId", s.locationGroupId)
            .putStr("locationId", s.locationId)
            .putStr("stopHeadsign", s.stopHeadsign)
            .putIntN("startPickupDropOffWindow", s.startPickupDropOffWindow)
            .putIntN("endPickupDropOffWindow", s.endPickupDropOffWindow)
            .putIntN("pickupType", s.pickupType)
            .putIntN("dropOffType", s.dropOffType)
            .putIntN("continuousPickup", s.continuousPickup)
            .putIntN("continuousDropOff", s.continuousDropOff)
            .putDblN("shapeDistTraveled", s.shapeDistTraveled)
            .putIntN("timepoint", s.timepoint)
            .putStr("pickupBookingRuleId", s.pickupBookingRuleId)
            .putStr("dropOffBookingRuleId", s.dropOffBookingRuleId)

    fun snapshot(
        json: JsonMapper,
        trip: Trip,
        stopTimes: List<StopTime>,
    ): ObjectNode {
        val node = json.createObjectNode()
        node.replace("trip", tripNode(json, trip))
        val arr = json.createArrayNode()
        stopTimes.forEach { arr.add(stopTimeNode(json, it)) }
        node.replace("stopTimes", arr)
        return node
    }

    /** Recreate a trip + all its stop times from a full snapshot, with fresh surrogate ids. */
    fun recreate(
        ctx: EditContext,
        tripJson: JsonNode,
        stopTimesJson: JsonNode,
    ) {
        val t =
            Trip(
                ctx.revisionId,
                routeId = tripJson.get("routeId").asString(),
                serviceId = tripJson.get("serviceId").asString(),
                tripId = tripJson.get("tripId").asString(),
                tripHeadsign = tripJson.strOrNull("tripHeadsign"),
                tripShortName = tripJson.strOrNull("tripShortName"),
                directionId = tripJson.intOrNull("directionId"),
                blockId = tripJson.strOrNull("blockId"),
                shapeId = tripJson.strOrNull("shapeId"),
                wheelchairAccessible = tripJson.intOrNull("wheelchairAccessible"),
                bikesAllowed = tripJson.intOrNull("bikesAllowed"),
            )
        ctx.trips.save(t)
        stopTimesJson.forEach { s ->
            ctx.stopTimes.save(
                StopTime(
                    ctx.revisionId,
                    tripId = t.tripId,
                    stopSequence = s.get("stopSequence").asInt(),
                    stopId = s.strOrNull("stopId"),
                    arrivalTime = s.intOrNull("arrivalTime"),
                    departureTime = s.intOrNull("departureTime"),
                    locationGroupId = s.strOrNull("locationGroupId"),
                    locationId = s.strOrNull("locationId"),
                    stopHeadsign = s.strOrNull("stopHeadsign"),
                    startPickupDropOffWindow = s.intOrNull("startPickupDropOffWindow"),
                    endPickupDropOffWindow = s.intOrNull("endPickupDropOffWindow"),
                    pickupType = s.intOrNull("pickupType"),
                    dropOffType = s.intOrNull("dropOffType"),
                    continuousPickup = s.intOrNull("continuousPickup"),
                    continuousDropOff = s.intOrNull("continuousDropOff"),
                    shapeDistTraveled = s.dblOrNull("shapeDistTraveled"),
                    timepoint = s.intOrNull("timepoint"),
                    pickupBookingRuleId = s.strOrNull("pickupBookingRuleId"),
                    dropOffBookingRuleId = s.strOrNull("dropOffBookingRuleId"),
                ),
            )
        }
    }

    fun deleteTrip(
        ctx: EditContext,
        tripId: String,
    ) {
        ctx.stopTimes.findByTripId(ctx.revisionId, tripId).let(ctx.stopTimes::deleteAll)
        ctx.trips.findByTripId(ctx.revisionId, tripId)?.let(ctx.trips::delete)
    }

    /**
     * Free trip id: the requested id if given and unused, else `${base}_copy`, `${base}_copy2`,
     * `${base}_copy3`, … until one is free in the draft.
     */
    fun resolveTripId(
        ctx: EditContext,
        requested: String?,
        base: String,
    ): String {
        if (requested != null && ctx.trips.findByTripId(ctx.revisionId, requested) == null) return requested
        var candidate = "${base}_copy"
        var n = 1
        while (ctx.trips.findByTripId(ctx.revisionId, candidate) != null) {
            n += 1
            candidate = "${base}_copy$n"
        }
        return candidate
    }

    /**
     * Dual-branch replay builder shared by all three ops: a `{ tripId }` direction deletes, a full
     * snapshot direction recreates. ADD/DUPLICATE journal the snapshot forward + `{tripId}` inverse;
     * DELETE journals the mirror.
     */
    fun registerBuilder(op: String) {
        EditOpRegistry.register(op) { ctx, dir ->
            {
                if (dir.has("trip")) {
                    recreate(ctx, dir.get("trip"), dir.get("stopTimes"))
                } else {
                    deleteTrip(ctx, dir.get("tripId").asString())
                }
            }
        }
    }
}

/**
 * Add a new trip to `routeId` under `serviceId` with a dense 1..n `stop_sequence`. Undo deletes the
 * trip and its stop times; redo recreates them from the journalled snapshot.
 */
class AddTripOp(
    private val routeId: String,
    private val serviceId: String,
    private val tripId: String?,
    private val headsign: String?,
    private val directionId: Int?,
    private val shapeId: String?,
    private val blockId: String?,
    private val stops: List<NewStopTime>,
) : EditOp {
    override val op = "ADD_TRIP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val newId = TripLifecycle.resolveTripId(ctx, tripId, "${routeId}_new")
        val trip =
            Trip(
                ctx.revisionId,
                routeId = routeId,
                serviceId = serviceId,
                tripId = newId,
                tripHeadsign = headsign,
                tripShortName = null,
                directionId = directionId,
                blockId = blockId,
                shapeId = shapeId,
                wheelchairAccessible = null,
                bikesAllowed = null,
            )
        val sts =
            stops.mapIndexed { i, s ->
                StopTime(
                    ctx.revisionId, newId, i + 1, s.stopId, s.arrivalSec, s.departureSec,
                    null, null, null, null, null, null, null, null, null, null, null, null, null,
                )
            }
        val fwd = TripLifecycle.snapshot(ctx.json, trip, sts)
        val inv = ctx.json.createObjectNode().put("tripId", newId)
        return PlannedEdit(
            "Add trip $newId to route $routeId",
            fwd,
            inv,
            mutate = {
                ctx.trips.save(trip)
                ctx.stopTimes.saveAll(sts)
            },
        )
    }

    companion object {
        init {
            TripLifecycle.registerBuilder("ADD_TRIP")
        }
    }
}

/**
 * Duplicate `sourceTripId` (all Trip columns + every stop time), shifting each non-null arrival and
 * departure by `offsetSec`. The new id is `newTripId` when free, else auto-suffixed from the source.
 */
class DuplicateTripOp(
    private val sourceTripId: String,
    private val newTripId: String?,
    private val offsetSec: Int,
) : EditOp {
    override val op = "DUPLICATE_TRIP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val src = ctx.trips.findByTripId(ctx.revisionId, sourceTripId) ?: error("no trip $sourceTripId")
        val srcSts = ctx.stopTimes.findByTripId(ctx.revisionId, sourceTripId)
        val newId = TripLifecycle.resolveTripId(ctx, newTripId, sourceTripId)
        val trip =
            Trip(
                ctx.revisionId,
                routeId = src.routeId,
                serviceId = src.serviceId,
                tripId = newId,
                tripHeadsign = src.tripHeadsign,
                tripShortName = src.tripShortName,
                directionId = src.directionId,
                blockId = src.blockId,
                shapeId = src.shapeId,
                wheelchairAccessible = src.wheelchairAccessible,
                bikesAllowed = src.bikesAllowed,
            )
        val sts =
            srcSts.map { s ->
                StopTime(
                    ctx.revisionId,
                    tripId = newId,
                    stopSequence = s.stopSequence,
                    stopId = s.stopId,
                    arrivalTime = s.arrivalTime?.plus(offsetSec),
                    departureTime = s.departureTime?.plus(offsetSec),
                    locationGroupId = s.locationGroupId,
                    locationId = s.locationId,
                    stopHeadsign = s.stopHeadsign,
                    startPickupDropOffWindow = s.startPickupDropOffWindow,
                    endPickupDropOffWindow = s.endPickupDropOffWindow,
                    pickupType = s.pickupType,
                    dropOffType = s.dropOffType,
                    continuousPickup = s.continuousPickup,
                    continuousDropOff = s.continuousDropOff,
                    shapeDistTraveled = s.shapeDistTraveled,
                    timepoint = s.timepoint,
                    pickupBookingRuleId = s.pickupBookingRuleId,
                    dropOffBookingRuleId = s.dropOffBookingRuleId,
                )
            }
        val fwd = TripLifecycle.snapshot(ctx.json, trip, sts)
        val inv = ctx.json.createObjectNode().put("tripId", newId)
        return PlannedEdit(
            "Duplicate trip $sourceTripId as $newId (${offsetSec / 60}m offset)",
            fwd,
            inv,
            mutate = {
                ctx.trips.save(trip)
                ctx.stopTimes.saveAll(sts)
            },
        )
    }

    companion object {
        init {
            TripLifecycle.registerBuilder("DUPLICATE_TRIP")
        }
    }
}

/**
 * Delete a trip and every stop time. The inverse carries a full snapshot of all columns so undo
 * re-creates them exactly (with fresh surrogate ids).
 */
class DeleteTripOp(
    private val tripId: String,
) : EditOp {
    override val op = "DELETE_TRIP"

    override fun plan(ctx: EditContext): PlannedEdit {
        val trip = ctx.trips.findByTripId(ctx.revisionId, tripId) ?: error("no trip $tripId")
        val sts = ctx.stopTimes.findByTripId(ctx.revisionId, tripId)
        val fwd = ctx.json.createObjectNode().put("tripId", tripId)
        val inv = TripLifecycle.snapshot(ctx.json, trip, sts)
        return PlannedEdit(
            "Delete trip $tripId (${sts.size} stops)",
            fwd,
            inv,
            mutate = {
                ctx.stopTimes.deleteAll(sts)
                ctx.trips.delete(trip)
            },
        )
    }

    companion object {
        init {
            TripLifecycle.registerBuilder("DELETE_TRIP")
        }
    }
}
