package eu.transittrack.gtfs.api.dto

import eu.transittrack.gtfs.model.Agency
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDate
import eu.transittrack.gtfs.model.FeedInfo
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.Level
import eu.transittrack.gtfs.model.Pathway
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.Shape
import eu.transittrack.gtfs.model.ShapePoint
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Transfer
import eu.transittrack.gtfs.model.Trip

/**
 * GraphQL projections of the core / shape / accessibility / misc GTFS entities.
 *
 * Every DTO carries `revisionId` + `feedCode` from the start (factory `of(entity, revisionId, feedCode)`); these two fields are intentionally absent from the schema types (Spring GraphQL ignores extra DTO properties) and exist so Task 23's `@SchemaMapping`
 * field resolvers can chain without DTO churn.
 *
 * GTFS clock times are stored as seconds-of-day (which may exceed 24h). They are exposed both as an `Int` (`*Seconds`) and as an `HH:MM:SS` string via [secondsToHms]; dates are rendered with `LocalDate.toString()` (ISO-8601).
 */
internal fun secondsToHms(s: Int?): String? =
    s?.let {
        "%02d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60)
    }

data class AgencyDto(
    val revisionId: Long,
    val feedCode: String,
    val agencyId: String?,
    val agencyName: String?,
    val agencyUrl: String?,
    val agencyTimezone: String?,
    val agencyLang: String?,
    val agencyPhone: String?,
    val agencyFareUrl: String?,
    val agencyEmail: String?,
    val extent: ExtentDto? = null,
) {
    companion object {
        fun of(
            e: Agency,
            revisionId: Long,
            feedCode: String,
        ) = AgencyDto(
            revisionId,
            feedCode,
            e.agencyId,
            e.agencyName,
            e.agencyUrl,
            e.agencyTimezone?.id,
            e.agencyLang?.toLanguageTag(),
            e.agencyPhone,
            e.agencyFareUrl,
            e.agencyEmail,
            ExtentDto.of(e.extent),
        )
    }
}

data class RouteDto(
    val revisionId: Long,
    val feedCode: String,
    val routeId: String,
    val agencyId: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeDesc: String?,
    val routeType: Int?,
    val routeUrl: String?,
    val routeColor: String?,
    val routeTextColor: String?,
    val routeSortOrder: Int?,
    val networkId: String?,
    val extent: ExtentDto? = null,
) {
    companion object {
        fun of(
            e: Route,
            revisionId: Long,
            feedCode: String,
        ) = RouteDto(
            revisionId,
            feedCode,
            e.routeId,
            e.agencyId,
            e.routeShortName,
            e.routeLongName,
            e.routeDesc,
            e.routeType,
            e.routeUrl,
            e.routeColor,
            e.routeTextColor,
            e.routeSortOrder,
            e.networkId,
            ExtentDto.of(e.extent),
        )
    }
}

data class StopDto(
    val revisionId: Long,
    val feedCode: String,
    val stopId: String,
    val stopCode: String?,
    val stopName: String?,
    val stopDesc: String?,
    val stopLat: Double?,
    val stopLon: Double?,
    val zoneId: String?,
    val locationType: Int?,
    val parentStation: String?,
    val wheelchairBoarding: Int?,
    val levelId: String?,
    val platformCode: String?,
) {
    companion object {
        fun of(
            e: Stop,
            revisionId: Long,
            feedCode: String,
        ) = StopDto(
            revisionId,
            feedCode,
            e.stopId,
            e.stopCode,
            e.stopName,
            e.stopDesc,
            e.stopLat,
            e.stopLon,
            e.zoneId,
            e.locationType,
            e.parentStation,
            e.wheelchairBoarding,
            e.levelId,
            e.platformCode,
        )
    }
}

data class TripDto(
    val revisionId: Long,
    val feedCode: String,
    val id: Long,
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val tripHeadsign: String?,
    val tripShortName: String?,
    val directionId: Int?,
    val blockId: String?,
    val shapeId: String?,
    val wheelchairAccessible: Int?,
    val bikesAllowed: Int?,
    val tripPatternId: Long?,
    val startTimeSec: Int?,
    val endTimeSec: Int?,
    val frequencyBased: Boolean?,
    val noSchedule: Boolean?,
) {
    companion object {
        fun of(
            e: Trip,
            revisionId: Long,
            feedCode: String,
        ) = TripDto(
            revisionId,
            feedCode,
            e.id!!,
            e.tripId,
            e.routeId,
            e.serviceId,
            e.tripHeadsign,
            e.tripShortName,
            e.directionId,
            e.blockId,
            e.shapeId,
            e.wheelchairAccessible,
            e.bikesAllowed,
            e.tripPatternId,
            e.startTimeSec,
            e.endTimeSec,
            e.frequencyBased,
            e.noSchedule,
        )
    }
}

data class StopTimeDto(
    val revisionId: Long,
    val feedCode: String,
    val tripId: String,
    val stopSequence: Int,
    val stopId: String?,
    val arrivalTime: String?,
    val arrivalTimeSeconds: Int?,
    val departureTime: String?,
    val departureTimeSeconds: Int?,
    val stopHeadsign: String?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val shapeDistTraveled: Double?,
    val timepoint: Int?,
) {
    companion object {
        fun of(
            e: StopTime,
            revisionId: Long,
            feedCode: String,
        ) = StopTimeDto(
            revisionId,
            feedCode,
            e.tripId,
            e.stopSequence,
            e.stopId,
            secondsToHms(e.arrivalTime),
            e.arrivalTime,
            secondsToHms(e.departureTime),
            e.departureTime,
            e.stopHeadsign,
            e.pickupType,
            e.dropOffType,
            e.shapeDistTraveled,
            e.timepoint,
        )
    }
}

data class CalendarDto(
    val revisionId: Long,
    val feedCode: String,
    val serviceId: String,
    val monday: Boolean?,
    val tuesday: Boolean?,
    val wednesday: Boolean?,
    val thursday: Boolean?,
    val friday: Boolean?,
    val saturday: Boolean?,
    val sunday: Boolean?,
    val startDate: String?,
    val endDate: String?,
) {
    companion object {
        fun of(
            e: Calendar,
            revisionId: Long,
            feedCode: String,
        ) = CalendarDto(
            revisionId,
            feedCode,
            e.serviceId,
            e.monday,
            e.tuesday,
            e.wednesday,
            e.thursday,
            e.friday,
            e.saturday,
            e.sunday,
            e.startDate?.toString(),
            e.endDate?.toString(),
        )
    }
}

data class CalendarDateDto(
    val revisionId: Long,
    val feedCode: String,
    val serviceId: String,
    val date: String,
    val exceptionType: Int?,
) {
    companion object {
        fun of(
            e: CalendarDate,
            revisionId: Long,
            feedCode: String,
        ) = CalendarDateDto(
            revisionId,
            feedCode,
            e.serviceId,
            e.date.toString(),
            e.exceptionType,
        )
    }
}

data class ShapePointDto(
    val revisionId: Long,
    val feedCode: String,
    val lat: Double?,
    val lon: Double?,
    val sequence: Int,
    val distTraveled: Double?,
) {
    companion object {
        fun of(
            e: ShapePoint,
            revisionId: Long,
            feedCode: String,
        ) = ShapePointDto(
            revisionId,
            feedCode,
            e.shapePtLat,
            e.shapePtLon,
            e.shapePtSequence,
            e.shapeDistTraveled,
        )
    }
}

data class ShapeDto(
    val revisionId: Long,
    val feedCode: String,
    val shapeId: String,
    val pointCount: Int,
    val lengthM: Double?,
    val points: List<ShapePointDto>,
) {
    companion object {
        fun of(
            shape: Shape,
            points: List<ShapePoint>,
            revisionId: Long,
            feedCode: String,
        ) = ShapeDto(
            revisionId,
            feedCode,
            shape.shapeId,
            shape.pointCount,
            shape.lengthM,
            points.map { ShapePointDto.of(it, revisionId, feedCode) },
        )
    }
}

data class FrequencyDto(
    val revisionId: Long,
    val feedCode: String,
    val tripId: String,
    val startTime: String,
    val endTime: String?,
    val headwaySecs: Int?,
    val exactTimes: Int?,
) {
    companion object {
        fun of(
            e: Frequency,
            revisionId: Long,
            feedCode: String,
        ) = FrequencyDto(
            revisionId,
            feedCode,
            e.tripId,
            secondsToHms(e.startTime)!!,
            secondsToHms(e.endTime),
            e.headwaySecs,
            e.exactTimes,
        )
    }
}

data class TransferDto(
    val revisionId: Long,
    val feedCode: String,
    val fromStopId: String?,
    val toStopId: String?,
    val fromRouteId: String?,
    val toRouteId: String?,
    val fromTripId: String?,
    val toTripId: String?,
    val transferType: Int?,
    val minTransferTime: Int?,
) {
    companion object {
        fun of(
            e: Transfer,
            revisionId: Long,
            feedCode: String,
        ) = TransferDto(
            revisionId,
            feedCode,
            e.fromStopId,
            e.toStopId,
            e.fromRouteId,
            e.toRouteId,
            e.fromTripId,
            e.toTripId,
            e.transferType,
            e.minTransferTime,
        )
    }
}

data class FeedInfoDto(
    val revisionId: Long,
    val feedCode: String,
    val feedPublisherName: String?,
    val feedPublisherUrl: String?,
    val feedLang: String?,
    val defaultLang: String?,
    val feedStartDate: String?,
    val feedEndDate: String?,
    val feedVersion: String?,
    val feedContactEmail: String?,
    val feedContactUrl: String?,
) {
    companion object {
        fun of(
            e: FeedInfo,
            revisionId: Long,
            feedCode: String,
        ) = FeedInfoDto(
            revisionId,
            feedCode,
            e.feedPublisherName,
            e.feedPublisherUrl,
            e.feedLang,
            e.defaultLang,
            e.feedStartDate?.toString(),
            e.feedEndDate?.toString(),
            e.feedVersion,
            e.feedContactEmail,
            e.feedContactUrl,
        )
    }
}

data class PathwayDto(
    val revisionId: Long,
    val feedCode: String,
    val pathwayId: String,
    val fromStopId: String?,
    val toStopId: String?,
    val pathwayMode: Int?,
    val isBidirectional: Int?,
    val length: Double?,
    val traversalTime: Int?,
    val stairCount: Int?,
    val maxSlope: Double?,
    val minWidth: Double?,
    val signpostedAs: String?,
    val reversedSignpostedAs: String?,
) {
    companion object {
        fun of(
            e: Pathway,
            revisionId: Long,
            feedCode: String,
        ) = PathwayDto(
            revisionId,
            feedCode,
            e.pathwayId,
            e.fromStopId,
            e.toStopId,
            e.pathwayMode,
            e.isBidirectional,
            e.length,
            e.traversalTime,
            e.stairCount,
            e.maxSlope,
            e.minWidth,
            e.signpostedAs,
            e.reversedSignpostedAs,
        )
    }
}

data class LevelDto(
    val revisionId: Long,
    val feedCode: String,
    val levelId: String,
    val levelIndex: Double?,
    val levelName: String?,
) {
    companion object {
        fun of(
            e: Level,
            revisionId: Long,
            feedCode: String,
        ) = LevelDto(
            revisionId,
            feedCode,
            e.levelId,
            e.levelIndex,
            e.levelName,
        )
    }
}
