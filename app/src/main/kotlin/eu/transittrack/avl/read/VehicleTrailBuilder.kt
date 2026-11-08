package eu.transittrack.avl.read

import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.rest.generated.model.LatLon
import eu.transittrack.rest.generated.model.TripSpeedSample
import eu.transittrack.rest.generated.model.VehicleStopStatus
import eu.transittrack.rest.generated.model.VehicleTrail
import eu.transittrack.avl.ingest.VehicleStopStatus as IngestStopStatus

/** The REST stop status for a stored report's status ordinal; `null` when absent or out of range. */
internal fun stopStatusOf(ordinal: Int?): VehicleStopStatus? =
    ordinal
        ?.let { runCatching { IngestStopStatus.entries[it] }.getOrNull() }
        ?.let { VehicleStopStatus.valueOf(it.name) }

/**
 * Summarises a vehicle's [reports] (newest first) for the vehicle page: the track it followed, how
 * often it reports, the newest report's status, and the speeds it reported while matched to
 * [currentTripRowId] (the trip it is on now), ordered by distance along that trip.
 *
 * Doing this server-side keeps the page from downloading and filtering a couple of hundred raw
 * reports, with their matches, on every poll.
 */
internal fun buildVehicleTrail(
    reports: List<AvlReportRow>,
    matchesByReportId: Map<Long, VehicleMatch>,
    currentTripRowId: Long?,
): VehicleTrail {
    val newest = reports.firstOrNull()
    val avgIntervalSec =
        if (reports.size < 2) {
            null
        } else {
            val first = reports.minOf { it.ts }
            val last = reports.maxOf { it.ts }
            (last.toEpochMilli() - first.toEpochMilli()) / (reports.size - 1) / 1000.0
        }
    val speeds =
        if (currentTripRowId == null) {
            emptyList()
        } else {
            reports
                .mapNotNull { report ->
                    val speed = report.speedMps ?: return@mapNotNull null
                    val match = matchesByReportId[report.id] ?: return@mapNotNull null
                    if (match.tripRowId != currentTripRowId || !match.distanceAlongTripM.isFinite()) return@mapNotNull null
                    TripSpeedSample(distanceAlongTripM = match.distanceAlongTripM, speedMps = speed)
                }.sortedBy { it.distanceAlongTripM }
        }
    return VehicleTrail(
        reportCount = reports.size,
        track = reports.sortedBy { it.ts }.map { LatLon(it.lat, it.lon) },
        speeds = speeds,
        latestReportTs = newest?.ts?.toString(),
        currentStatus = stopStatusOf(newest?.currentStatus),
        descTripId = newest?.descTripId,
        avgReportIntervalSec = avgIntervalSec,
    )
}
