package eu.transittrack.avl.ingest

import java.time.Instant
import java.time.LocalDate

import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.RawAvlPayload

enum class RtScheduleRelationship {
    SCHEDULED,
    ADDED,
    UNSCHEDULED,
    CANCELED,
    DUPLICATED,
}

enum class VehicleStopStatus {
    INCOMING_AT,
    STOPPED_AT,
    IN_TRANSIT_TO,
}

enum class AvlOccupancyStatus {
    EMPTY,
    MANY_SEATS_AVAILABLE,
    FEW_SEATS_AVAILABLE,
    STANDING_ROOM_ONLY,
    CRUSHED_STANDING_ROOM_ONLY,
    FULL,
    NOT_ACCEPTING_PASSENGERS,
    NO_DATA_AVAILABLE,
    NOT_BOARDABLE,
}

enum class AvlCongestionLevel {
    UNKNOWN_CONGESTION_LEVEL,
    RUNNING_SMOOTHLY,
    STOP_AND_GO,
    CONGESTION,
    SEVERE_CONGESTION,
}

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
    val descScheduleRelationship: RtScheduleRelationship? = null,
    val currentStopSequence: Int? = null,
    val currentStopId: String? = null,
    val currentStatus: VehicleStopStatus? = null,
    val occupancyStatus: AvlOccupancyStatus? = null,
    val congestionLevel: AvlCongestionLevel? = null,
)

data class FeedDescriptor(
    val code: String,
    val name: String,
    val url: String,
    val format: AvlFormat,
    val headers: Map<String, String>?,
)

interface AvlFeedDecoder {
    val format: AvlFormat

    fun decode(
        payload: RawAvlPayload,
        feed: FeedDescriptor,
    ): List<AvlReport>
}

class AvlFetchException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AvlDecodeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
