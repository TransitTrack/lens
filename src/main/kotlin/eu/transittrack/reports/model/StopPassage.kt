package eu.transittrack.reports.model

import java.time.LocalDate

/**
 * One scheduled stop of one trip execution, as derived on-demand from AVL match history by
 * [eu.transittrack.reports.read.StopPassageDerivationService]. Not a JPA entity - purely an
 * in-memory shape returned from derivation, never persisted.
 *
 * [observedSec]/[adherenceSec] are `null` when the trip never reached this stop (by
 * [stopPathIndex]) in the queried AVL match window.
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
