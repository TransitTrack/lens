package eu.transittrack.avl.match

import java.time.Instant

/** Lean, JPA-free mirror of `eu.transittrack.avl.model.VehicleState`. */
data class VehicleStateView(
    val id: Long?,
    val feedId: Long,
    val vehicleId: String,
    val vehicleLabel: String?,
    val reportTs: Instant,
    val lat: Double,
    val lon: Double,
    val bearing: Double?,
    val speedMps: Double?,
    val occupancyStatus: Int?,
    val matched: Boolean,
    val stale: Boolean,
    val consecutiveFailures: Int,
    val revisionId: Long?,
    val tripRowId: Long?,
    val blockPk: Long?,
    val tripPatternId: Long?,
    val stopPathIndex: Int?,
    val distanceAlongTripM: Double?,
    val scheduleAdherenceSec: Int?,
    val snappedLat: Double?,
    val snappedLon: Double?,
)
