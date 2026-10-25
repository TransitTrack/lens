package eu.transittrack.reports.model

import java.time.LocalDate

import eu.transittrack.avl.ingest.AvlOccupancyStatus

/**
 * One scheduled stop of one trip execution, as derived on-demand from AVL match history by
 * [eu.transittrack.reports.read.StopPassageDerivationService]. Not a JPA entity - purely an
 * in-memory shape returned from derivation, never persisted.
 *
 * [observedSec]/[adherenceSec] are `null` when the trip never reached this stop (by
 * [stopPathIndex]) in the queried AVL match window.
 *
 * [observedSec] and [adherenceSec] are NOT guaranteed to be algebraically consistent with
 * [scheduledSec]: [observedSec] is the raw matched ping's timestamp converted to service-seconds,
 * while [adherenceSec] is [eu.transittrack.avl.model.VehicleMatch.scheduleAdherenceSec] -
 * interpolated at the vehicle's actual along-trip position at ping time, which may sit slightly
 * before or after this exact stop. Do not assume `observedSec - scheduledSec == adherenceSec`.
 *
 * [vehicleId] is the group's own key (every passage in a trip execution's group shares one
 * vehicle, including rows where [observedSec] is `null`), [direction] is the trip's own
 * `directionId` (constant across every passage of one trip execution), and [occupancyStatus] is
 * read off the specific AVL report row that produced the observed passage (`null` whenever
 * [observedSec] is `null`, or if that report carried no occupancy field). [stopName] is read off
 * the matched trip's own revision's `Stop` row for [stopId] (`null` if that GTFS feed never gave
 * the stop a name, same as [eu.transittrack.gtfs.model.Stop.stopName] itself).
 *
 * **Known Phase 1 limitation - duplicate rows on vehicle swap**: derivation groups matches by
 * `(tripId, serviceDate, vehicleId)` before producing one [StopPassage] per scheduled stop per
 * group. In a rare vehicle-swap or duplicate AVL-assignment scenario, the same trip execution's
 * matches can be split across more than one `vehicleId` for the same
 * `(tripId, serviceDate, stopPathIndex)`, producing more than one [StopPassage] for that same
 * logical stop in the result. Accepted as a rare edge case, not fixed in Phase 1.
 */
data class StopPassage(
    val tripId: String,
    val routeId: String,
    val blockId: String?,
    val serviceDate: LocalDate,
    val stopId: String,
    val stopName: String?,
    val stopPathIndex: Int,
    val scheduledSec: Int?,
    val observedSec: Int?,
    val adherenceSec: Int?,
    val vehicleId: String,
    val direction: Int?,
    val occupancyStatus: AvlOccupancyStatus?,
)

/**
 * Route-level schedule adherence aggregate over a date range, computed by
 * [eu.transittrack.reports.read.AdherenceReadService] from [StopPassage.adherenceSec] samples
 * (excluding the `null` ones - see [StopPassage]'s KDoc). [sampleCount] is the number of non-null
 * samples a route had; a route with zero samples produces no summary at all rather than a
 * divide-by-zero or NaN result.
 */
data class RouteAdherenceSummary(
    val routeId: String,
    val routeShortName: String?,
    val sampleCount: Int,
    val meanAdherenceSec: Double,
    val onTimePct: Double,
)

/**
 * Trip-execution-level schedule adherence aggregate (one [TripAdherenceSummary] per
 * `(tripId, serviceDate)`), computed the same way as [RouteAdherenceSummary] but grouped more
 * finely. See [RouteAdherenceSummary]'s KDoc for the sample-exclusion and zero-sample behavior.
 */
data class TripAdherenceSummary(
    val tripId: String,
    val routeId: String,
    val serviceDate: LocalDate,
    val sampleCount: Int,
    val meanAdherenceSec: Double,
    val onTimePct: Double,
)
