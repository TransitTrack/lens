package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate

/** Lean, JPA-free mirror of `eu.transittrack.avl.model.AvlReportRow`. */
data class AvlReportView(
    val id: Long,
    val feedId: Long,
    val vehicleId: String,
    val vehicleLabel: String?,
    val ts: Instant,
    val lat: Double,
    val lon: Double,
    val bearing: Double?,
    val speedMps: Double?,
    val odometerM: Double?,
    val descTripId: String?,
    val descRouteId: String?,
    val descDirectionId: Int?,
    val descStartDate: LocalDate?,
    val descStartTimeSec: Int?,
    val descScheduleRelationship: Int?,
    val currentStopSequence: Int?,
    val currentStopId: String?,
    val currentStatus: Int?,
    val occupancyStatus: Int?,
    val congestionLevel: Int?,
)
