package eu.transittrack.schedule.model

/** Lean, JPA-free mirror of `eu.transittrack.schedule.model.StopPath`. */
data class StopPathRef(
    val id: Long,
    val revisionId: Long,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val stopId: String,
    val routeId: String?,
    val stopSeq: Int,
    val lengthM: Double,
    val pickupType: Int?,
    val dropOffType: Int?,
    val waitStop: Boolean,
    val scheduleAdherenceStop: Boolean,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
)
