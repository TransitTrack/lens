package eu.transittrack.gtfs.draft.edit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Trip

/** One stop of a brand-new trip built by [AddTripOp]; both times are required. */
data class NewStopTime(
    val stopId: String,
    val arrivalSec: Int,
    val departureSec: Int,
)

private fun JsonObject.strOrNull(field: String): String? = get(field)?.jsonPrimitive?.contentOrNull

private fun JsonObject.nullableInt(field: String): Int? = get(field)?.takeUnless { it == JsonNull }?.jsonPrimitive?.int

/**
 * Shared machinery for the trip-lifecycle ops. A "full snapshot" JSON node is
 * `{ "trip": <all 10 non-derived Trip columns>, "stopTimes": [<all 18 non-revision StopTime columns>] }`
 * and round-trips every column so a delete -> undo (or an undo -> redo) restores rows byte-for-byte
 * (only the surrogate ids change, re-issued by the sequence on `save`).
 */
internal object TripLifecycle {
    fun tripNode(t: Trip): JsonObject =
        buildJsonObject {
            put("routeId", t.routeId)
            put("serviceId", t.serviceId)
            put("tripId", t.tripId)
            put("tripHeadsign", t.tripHeadsign)
            put("tripShortName", t.tripShortName)
            put("directionId", t.directionId)
            put("blockId", t.blockId)
            put("shapeId", t.shapeId)
            put("wheelchairAccessible", t.wheelchairAccessible)
            put("bikesAllowed", t.bikesAllowed)
        }

    // stop-time (de)serialisation lives entirely in StopTimeSnapshot — call it directly.

    fun frequencyNode(f: Frequency): JsonObject =
        buildJsonObject {
            put("tripId", f.tripId)
            put("startTime", f.startTime)
            put("endTime", f.endTime)
            put("headwaySecs", f.headwaySecs)
            put("exactTimes", f.exactTimes)
        }

    /** Build a fresh (unsaved) [Frequency] from a node, under `ctx.revisionId`, retargeted to `tripId`. */
    fun frequencyFromNode(
        ctx: EditContext,
        node: JsonObject,
        tripId: String = node.getValue("tripId").jsonPrimitive.content,
    ): Frequency =
        Frequency(
            ctx.revisionId,
            tripId = tripId,
            startTime = node.getValue("startTime").jsonPrimitive.int,
            endTime = node.nullableInt("endTime"),
            headwaySecs = node.nullableInt("headwaySecs"),
            exactTimes = node.nullableInt("exactTimes"),
        )

    fun snapshot(
        trip: Trip,
        stopTimes: List<StopTime>,
        frequencies: List<Frequency> = emptyList(),
    ): JsonObject =
        buildJsonObject {
            put("trip", tripNode(trip))
            put("stopTimes", JsonArray(stopTimes.map(StopTimeSnapshot::toNode)))
            put("frequencies", JsonArray(frequencies.map(::frequencyNode)))
        }

    /** Recreate a trip + all its stop times (+ frequencies) from a full snapshot, with fresh surrogate ids. */
    fun recreate(
        ctx: EditContext,
        tripJson: JsonObject,
        stopTimesJson: JsonArray,
        frequenciesJson: JsonArray? = null,
    ) {
        val t =
            Trip(
                ctx.revisionId,
                routeId = tripJson.getValue("routeId").jsonPrimitive.content,
                serviceId = tripJson.getValue("serviceId").jsonPrimitive.content,
                tripId = tripJson.getValue("tripId").jsonPrimitive.content,
                tripHeadsign = tripJson.strOrNull("tripHeadsign"),
                tripShortName = tripJson.strOrNull("tripShortName"),
                directionId = tripJson.nullableInt("directionId"),
                blockId = tripJson.strOrNull("blockId"),
                shapeId = tripJson.strOrNull("shapeId"),
                wheelchairAccessible = tripJson.nullableInt("wheelchairAccessible"),
                bikesAllowed = tripJson.nullableInt("bikesAllowed"),
            )
        val restoredStops = stopTimesJson.map { s -> StopTimeSnapshot.fromNode(ctx, s.jsonObject) }
        val restoredFrequencies = frequenciesJson?.map { f -> frequencyFromNode(ctx, f.jsonObject) }.orEmpty()
        ctx.trips.save(t)
        restoredStops.forEach { ctx.stopTimes.save(it) }
        restoredFrequencies.forEach { ctx.frequencies.save(it) }
    }

    fun deleteTrip(
        ctx: EditContext,
        tripId: String,
    ) {
        ctx.stopTimes.findByTripId(ctx.revisionId, tripId).let(ctx.stopTimes::deleteAll)
        ctx.frequencies.findByTripId(ctx.revisionId, tripId).let(ctx.frequencies::deleteAll)
        ctx.trips.findByTripId(ctx.revisionId, tripId)?.let(ctx.trips::delete)
    }

    /**
     * Free trip id. The stem is the requested id when given, else `base`. Try the stem verbatim
     * first, then `${stem}_copy`, `${stem}_copy2`, `${stem}_copy3`, … until one is free in the draft.
     */
    fun resolveTripId(
        ctx: EditContext,
        requested: String?,
        base: String,
    ): String {
        val stem = requested ?: base
        if (ctx.trips.findByTripId(ctx.revisionId, stem) == null) return stem
        var candidate = "${stem}_copy"
        var n = 1
        while (ctx.trips.findByTripId(ctx.revisionId, candidate) != null) {
            n += 1
            candidate = "${stem}_copy$n"
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
                val fields = dir.jsonObject
                if ("trip" in fields) {
                    recreate(
                        ctx,
                        fields.getValue("trip").jsonObject,
                        fields.getValue("stopTimes").jsonArray,
                        fields["frequencies"]?.takeUnless { it == JsonNull }?.jsonArray,
                    )
                } else {
                    deleteTrip(ctx, fields.getValue("tripId").jsonPrimitive.content)
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
        val fwd = TripLifecycle.snapshot(trip, sts)
        val inv = buildJsonObject { put("tripId", newId) }
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
        // faithful duplicate: source stop_sequence values are copied verbatim, not re-densified 1..n
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
        // Frequencies define a headway service window: copy verbatim onto the new trip id,
        // do NOT offset start/end by offsetSec.
        val freqs =
            ctx.frequencies.findByTripId(ctx.revisionId, sourceTripId).map { f ->
                Frequency(ctx.revisionId, newId, f.startTime, f.endTime, f.headwaySecs, f.exactTimes)
            }
        val fwd = TripLifecycle.snapshot(trip, sts, freqs)
        val inv = buildJsonObject { put("tripId", newId) }
        return PlannedEdit(
            "Duplicate trip $sourceTripId as $newId (${offsetSec / 60}m offset)",
            fwd,
            inv,
            mutate = {
                ctx.trips.save(trip)
                ctx.stopTimes.saveAll(sts)
                ctx.frequencies.saveAll(freqs)
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
        val freqs = ctx.frequencies.findByTripId(ctx.revisionId, tripId)
        val fwd = buildJsonObject { put("tripId", tripId) }
        val inv = TripLifecycle.snapshot(trip, sts, freqs)
        return PlannedEdit(
            "Delete trip $tripId (${sts.size} stops)",
            fwd,
            inv,
            mutate = {
                ctx.stopTimes.deleteAll(sts)
                ctx.frequencies.deleteAll(freqs)
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
