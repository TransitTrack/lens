package eu.transittrack.schedule.model

/** Lean, JPA-free mirror of `eu.transittrack.schedule.model.BlockTrip`. */
data class BlockTripRef(
    val id: Long,
    val revisionId: Long,
    val blockId: Long,
    val tripId: Long,
    val listIndex: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
)
