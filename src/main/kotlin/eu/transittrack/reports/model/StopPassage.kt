package eu.transittrack.reports.model

import java.time.LocalDate

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
 */
data class StopPassage(
    val tripId: String,
    val routeId: String,
    val blockId: String?,
    val serviceDate: LocalDate,
    val stopId: String,
    val stopPathIndex: Int,
    val scheduledSec: Int?,
    val observedSec: Int?,
    val adherenceSec: Int?,
)
