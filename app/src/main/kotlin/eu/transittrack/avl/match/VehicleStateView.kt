package eu.transittrack.avl.match

import java.time.Instant

import eu.transittrack.geo.Point

/**
 * Read-only, persistence-free view of a vehicle's last known state, as handed to matchers.
 *
 * Implemented directly by `eu.transittrack.avl.model.VehicleState` (the stored row) and by
 * `eu.transittrack.avl.ingest.VehicleStateUpsert` (the state just computed), so neither needs a
 * field-by-field copy.
 */
interface VehicleStateView {
    val feedCode: String
    val vehicleId: String
    val vehicleLabel: String?
    val reportTs: Instant
    val lat: Double
    val lon: Double
    val bearing: Double?
    val speedMps: Double?
    val occupancyStatus: Int?
    val matched: Boolean
    val stale: Boolean
    val consecutiveFailures: Int
    val revisionId: Long?
    val tripRowId: Long?
    val blockPk: Long?
    val tripPatternId: Long?
    val stopPathIndex: Int?
    val distanceAlongTripM: Double?
    val scheduleAdherenceSec: Int?
    val snappedLat: Double?
    val snappedLon: Double?

    /** The report's position. Derived from [lat]/[lon] on access, so it can't go stale if they change. */
    val point: Point get() = Point(lat, lon)
}
