package eu.transittrack.schedule.read.dto

import eu.transittrack.gtfs.api.dto.ExtentDto
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.SchedTrip
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.TripPattern

data class TripPatternDto(
    val revisionId: Long,
    val feedCode: String,
    val patternKey: String,
    val routeId: String,
    val directionId: Int?,
    val headsign: String?,
    val shapeId: String?,
    val stopCount: Int,
    val lengthM: Double?,
    val tripCount: Int,
    val extent: ExtentDto?,
    val id: Long,
) {
    companion object {
        fun of(
            e: TripPattern,
            revisionId: Long,
            feedCode: String,
        ) = TripPatternDto(
            revisionId,
            feedCode,
            e.patternKey,
            e.routeId,
            e.directionId,
            e.headsign,
            e.shapeId,
            e.stopCount,
            e.lengthM,
            e.tripCount,
            ExtentDto.of(e.extent),
            e.id!!,
        )
    }
}

data class StopPathDto(
    val revisionId: Long,
    val feedCode: String,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val stopId: String,
    val gtfsStopSeq: Int,
    val lengthM: Double,
    val pathGeometry: Any?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val waitStop: Boolean,
    val scheduleAdherenceStop: Boolean,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
) {
    companion object {
        fun of(
            e: StopPath,
            revisionId: Long,
            feedCode: String,
            geometry: Any?,
        ) = StopPathDto(
            revisionId,
            feedCode,
            e.tripPatternId,
            e.stopPathIndex,
            e.stopId,
            e.gtfsStopSeq,
            e.lengthM,
            geometry,
            e.pickupType,
            e.dropOffType,
            e.waitStop,
            e.scheduleAdherenceStop,
            e.layoverStop,
            e.breakTimeSec,
        )
    }
}

data class SchedTripDto(
    val revisionId: Long,
    val feedCode: String,
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val directionId: Int?,
    val headsign: String?,
    val tripShortName: String?,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val frequencyBased: Boolean,
    val exactTimes: Int?,
    val tripPatternId: Long,
    val id: Long,
) {
    companion object {
        fun of(
            e: SchedTrip,
            revisionId: Long,
            feedCode: String,
        ) = SchedTripDto(
            revisionId,
            feedCode,
            e.tripId,
            e.routeId,
            e.serviceId,
            e.directionId,
            e.headsign,
            e.tripShortName,
            e.startTimeSec,
            e.endTimeSec,
            e.frequencyBased,
            e.exactTimes,
            e.tripPatternId,
            e.id!!,
        )
    }
}

data class ScheduleTimeDto(
    val revisionId: Long,
    val feedCode: String,
    val stopPathIndex: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
    val interpolated: Boolean,
    val schedTravelTimeSec: Int?,
    val schedDwellTimeSec: Int?,
) {
    companion object {
        fun of(
            e: ScheduleTime,
            revisionId: Long,
            feedCode: String,
        ) = ScheduleTimeDto(
            revisionId,
            feedCode,
            e.stopPathIndex,
            e.arrivalSec,
            e.departureSec,
            e.interpolated,
            e.schedTravelTimeSec,
            e.schedDwellTimeSec,
        )
    }
}

data class BlockDto(
    val revisionId: Long,
    val feedCode: String,
    val id: Long,
    val blockId: String,
    val serviceId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val tripCount: Int,
    val routeIds: List<String>,
) {
    companion object {
        fun of(
            e: Block,
            revisionId: Long,
            feedCode: String,
        ) = BlockDto(
            revisionId,
            feedCode,
            e.id!!,
            e.blockId,
            e.serviceId,
            e.startTimeSec,
            e.endTimeSec,
            e.tripCount,
            e.routeIds,
        )
    }
}

data class BlockTripDto(
    val revisionId: Long,
    val feedCode: String,
    val listIndex: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
    val schedTripId: Long,
) {
    companion object {
        fun of(
            e: eu.transittrack.schedule.model.BlockTrip,
            revisionId: Long,
            feedCode: String,
        ) = BlockTripDto(
            revisionId,
            feedCode,
            e.listIndex,
            e.layoverAfterSec,
            e.deadheadAfter,
            e.schedTripId,
        )
    }
}
