package eu.transittrack.avl.read.dto

import eu.transittrack.avl.ingest.AvlOccupancyStatus
import eu.transittrack.avl.ingest.VehicleStopStatus
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleState

data class LatLonDto(
    val lat: Double,
    val lon: Double,
)

data class AvlFeedDto(
    val code: String,
    val name: String,
    val gtfsFeedCode: String,
    val format: String,
    val pollIntervalSec: Int,
    val assignmentMode: String,
    val enabled: Boolean,
    val lastPollAt: String?,
    val lastPollStatus: String?,
    val lastPollReportCount: Int?,
) {
    companion object {
        fun of(f: AvlFeed) =
            AvlFeedDto(
                f.code,
                f.name,
                f.gtfsFeedCode,
                f.format.name,
                f.pollIntervalSec,
                f.assignmentMode.name,
                f.enabled,
                f.lastPollAt?.toString(),
                f.lastPollStatus,
                f.lastPollReportCount,
            )
    }
}

data class VehicleDto(
    val vehicleId: String,
    val label: String?,
    val reportTs: String,
    val position: LatLonDto,
    val snappedPosition: LatLonDto?,
    val bearing: Double?,
    val speedMps: Double?,
    val occupancyStatus: String?,
    val matched: Boolean,
    val stale: Boolean,
    val scheduleAdherenceSec: Int?,
    val stopPathIndex: Int?,
    val distanceAlongTripM: Double?,
    // resolver-only, not exposed as schema fields:
    val gtfsFeedCode: String,
    val revisionId: Long?,
    val tripRowId: Long?,
    val blockPk: Long?,
    val tripPatternId: Long?,
) {
    companion object {
        fun of(
            s: VehicleState,
            gtfsFeedCode: String,
        ) = VehicleDto(
            vehicleId = s.vehicleId,
            label = s.vehicleLabel,
            reportTs = s.reportTs.toString(),
            position = LatLonDto(s.lat, s.lon),
            snappedPosition =
                if (s.snappedLat != null && s.snappedLon != null) {
                    LatLonDto(s.snappedLat!!, s.snappedLon!!)
                } else {
                    null
                },
            bearing = s.bearing,
            speedMps = s.speedMps,
            occupancyStatus =
                s.occupancyStatus?.let {
                    runCatching { AvlOccupancyStatus.entries[it].name }.getOrNull()
                },
            matched = s.matched,
            stale = s.stale,
            scheduleAdherenceSec = s.scheduleAdherenceSec,
            stopPathIndex = s.stopPathIndex,
            distanceAlongTripM = s.distanceAlongTripM,
            gtfsFeedCode = gtfsFeedCode,
            revisionId = s.revisionId,
            tripRowId = s.tripRowId,
            blockPk = s.blockPk,
            tripPatternId = s.tripPatternId,
        )
    }
}

data class AvlReportDto(
    val vehicleId: String,
    val ts: String,
    val position: LatLonDto,
    val bearing: Double?,
    val speedMps: Double?,
    val currentStatus: String?,
    val occupancyStatus: String?,
    val descTripId: String?,
) {
    companion object {
        fun of(r: AvlReportRow) =
            AvlReportDto(
                vehicleId = r.vehicleId,
                ts = r.ts.toString(),
                position = LatLonDto(r.lat, r.lon),
                bearing = r.bearing,
                speedMps = r.speedMps,
                currentStatus =
                    r.currentStatus?.let {
                        runCatching { VehicleStopStatus.entries[it].name }.getOrNull()
                    },
                occupancyStatus =
                    r.occupancyStatus?.let {
                        runCatching { AvlOccupancyStatus.entries[it].name }.getOrNull()
                    },
                descTripId = r.descTripId,
            )
    }
}
