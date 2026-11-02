package eu.transittrack.gtfs.draft.edit

import eu.transittrack.gtfs.model.Trip

/**
 * Shift a filtered set of trips by `deltaSec` in a single journal entry. The filtered set is:
 *
 * 1. a primary set — `patternKey` (resolved to a `trip_pattern_id`) if given, else `routeId`, else
 *    `serviceId`, else every trip of the revision;
 * 2. narrowed by whichever of `routeId` / `serviceId` was not the primary selector;
 * 3. narrowed to trips whose minimum non-null `departure_time` across their stop times falls within
 *    `[windowFromSec, windowToSec]` (either bound null = open).
 *
 * Each candidate trip is shifted independently with the same clamp-at-0 rule as [ShiftTripOp]; the
 * forward and inverse JSON carry the *actual applied* per-trip delta so undo lands exactly back on
 * the original values. `patternKey` filtering requires a fresh derivation ([needsFreshDerivation]).
 */
class BulkShiftTripsOp(
    private val routeId: String?,
    private val patternKey: String?,
    private val serviceId: String?,
    private val windowFromSec: Int?,
    private val windowToSec: Int?,
    private val deltaSec: Int,
) : EditOp {
    override val op = "BULK_SHIFT"

    override val needsFreshDerivation: Boolean get() = patternKey != null

    override fun plan(ctx: EditContext): PlannedEdit {
        val primary: List<Trip> =
            when {
                patternKey != null -> {
                    val patternId =
                        ctx.tripPatterns.findByPatternKey(ctx.revisionId, patternKey)?.id
                            ?: throw IllegalArgumentException("no pattern '$patternKey'")
                    ctx.trips.findByTripPattern(ctx.revisionId, patternId)
                }

                routeId != null -> {
                    ctx.trips.findByRouteId(ctx.revisionId, routeId)
                }

                serviceId != null -> {
                    ctx.trips.findByServiceId(ctx.revisionId, serviceId)
                }

                else -> {
                    ctx.trips.findByRevisionId(ctx.revisionId)
                }
            }

        val narrowed =
            primary.filter { t ->
                (routeId == null || t.routeId == routeId) &&
                    (serviceId == null || t.serviceId == serviceId)
            }

        val candidates =
            narrowed.filter { t ->
                if (windowFromSec == null && windowToSec == null) {
                    return@filter true
                }
                val minDep =
                    ctx.stopTimes
                        .findByTripId(ctx.revisionId, t.tripId)
                        .mapNotNull { it.departureTime }
                        .minOrNull() ?: return@filter false
                (windowFromSec == null || minDep >= windowFromSec) &&
                    (windowToSec == null || minDep <= windowToSec)
            }

        val shifts =
            candidates.map { t ->
                val rows = ctx.stopTimes.findByTripId(ctx.revisionId, t.tripId)
                val minTime =
                    rows
                        .flatMap { listOfNotNull(it.arrivalTime, it.departureTime) }
                        .minOrNull() ?: 0
                val applied = if (deltaSec < 0) maxOf(deltaSec, -minTime) else deltaSec
                t.tripId to applied
            }

        val fwdArr = ctx.json.createArrayNode()
        val invArr = ctx.json.createArrayNode()
        shifts.forEach { (tripId, applied) ->
            fwdArr.add(
                ctx.json
                    .createObjectNode()
                    .put("tripId", tripId)
                    .put("delta", applied),
            )
            invArr.add(
                ctx.json
                    .createObjectNode()
                    .put("tripId", tripId)
                    .put("delta", -applied),
            )
        }
        val fwd = ctx.json.createObjectNode()
        fwd.replace("shifts", fwdArr)
        val inv = ctx.json.createObjectNode()
        inv.replace("shifts", invArr)

        return PlannedEdit(
            "Shift ${shifts.size} trips by ${if (deltaSec >= 0) "+" else ""}${deltaSec / 60}m",
            fwd,
            inv,
            mutate = { shifts.forEach { (tripId, applied) -> ShiftTripOp.shift(ctx, tripId, applied) } },
        )
    }

    companion object {
        init {
            EditOpRegistry.register("BULK_SHIFT") { ctx, dir ->
                {
                    dir.get("shifts").forEach { el ->
                        ShiftTripOp.shift(ctx, el.get("tripId").asString(), el.get("delta").asInt())
                    }
                }
            }
        }
    }
}
