package eu.transittrack.avl.match

import java.time.Instant
import java.time.LocalDate

import eu.transittrack.geo.Point

/**
 * Read-only, persistence-free view of one AVL report, as handed to matchers and extensions.
 *
 * Implemented directly by `eu.transittrack.avl.model.AvlReportRow`, so a report is a single object
 * rather than a row plus a field-by-field copy.
 */
interface AvlReportView {
    val id: Long?
    val feedCode: String
    val vehicleId: String
    val vehicleLabel: String?
    val ts: Instant
    val lat: Double
    val lon: Double
    val bearing: Double?
    val speedMps: Double?
    val odometerM: Double?
    val descTripId: String?
    val descRouteId: String?
    val descDirectionId: Int?
    val descStartDate: LocalDate?
    val descStartTimeSec: Int?
    val descScheduleRelationship: Int?
    val currentStopSequence: Int?
    val currentStopId: String?
    val currentStatus: Int?
    val occupancyStatus: Int?
    val congestionLevel: Int?

    /** The report's position. Derived from [lat]/[lon] on access, so it can't go stale if they change. */
    val point: Point get() = Point(lat, lon)
}
