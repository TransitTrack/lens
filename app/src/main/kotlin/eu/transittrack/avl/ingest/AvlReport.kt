package eu.transittrack.avl.ingest

import java.time.Instant
import java.time.LocalDate

data class AvlReport(
    val vehicleId: String,
    val vehicleLabel: String?,
    val ts: Instant,
    val lat: Double,
    val lon: Double,
    val bearing: Double? = null,
    val speedMps: Double? = null,
    val odometerM: Double? = null,
    val descTripId: String? = null,
    val descRouteId: String? = null,
    val descDirectionId: Int? = null,
    val descStartDate: LocalDate? = null,
    val descStartTimeSec: Int? = null,
    val descScheduleRelationship: RealtimeScheduleRelationship? = null,
    val currentStopSequence: Int? = null,
    val currentStopId: String? = null,
    val currentStatus: VehicleStopStatus? = null,
    val occupancyStatus: AvlOccupancyStatus? = null,
    val congestionLevel: AvlCongestionLevel? = null,
)
