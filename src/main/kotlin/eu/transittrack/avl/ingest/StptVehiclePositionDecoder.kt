package eu.transittrack.avl.ingest

import java.time.Instant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.util.kmphToMps

@Serializable
private data class StptVehiclePositionResponse(
    val success: Boolean,
    val data: Data,
)

@Serializable
private data class Data(
    val vehicles: List<Vehicle>,
    val total: Long,
    @SerialName("generated_at")
    val generatedAt: Long,
    @SerialName("server_time")
    val serverTime: Long,
    @SerialName("age_seconds")
    val ageSeconds: Long,
    @SerialName("is_stale")
    val isStale: Boolean,
    @SerialName("service_expected_now")
    val serviceExpectedNow: Boolean,
    @SerialName("service_window")
    val serviceWindow: ServiceWindow,
    @SerialName("upstream_status")
    val upstreamStatus: String,
//    @SerialName("upstream_status_reason")
//    val upstreamStatusReason: Any?,
    @SerialName("live_status")
    val liveStatus: String,
//    @SerialName("live_status_reason")
//    val liveStatusReason: Any?,
    @SerialName("newest_vehicle_ts")
    val newestVehicleTs: Long,
    @SerialName("vehicle_data_age_seconds")
    val vehicleDataAgeSeconds: Long,
)

@Serializable
private data class Vehicle(
    val id: String,
    val lat: Double,
    val lng: Double,
    val bearing: Double,
    val speed: Double,
    val route: String,
    val directionId: String,
    val headsign: String,
    val stop: String,
    val timestamp: Long,
    val isAccessible: Boolean,
    val routeId: String,
    val tripId: String,
    val shapeId: String,
)

@Serializable
private data class ServiceWindow(
    @SerialName("first_departure")
    val firstDeparture: String,
    @SerialName("last_arrival")
    val lastArrival: String,
)

@Component
class StptVehiclePositionDecoder(
    val jsonMapper: Json,
) : AvlFeedDecoder {
    override val format: AvlFormat
        get() = AvlFormat.STPT

    override fun decode(
        payload: RawAvlPayload,
        feed: FeedDescriptor,
    ): List<AvlReport> {
        val response = payload.bytes
            .toString(Charsets.UTF_8)
            .let { jsonMapper.decodeFromString<StptVehiclePositionResponse>(it) }
            .takeIf { it.success }

        return response
            ?.data
            ?.vehicles
            ?.map {
                AvlReport(
                    vehicleId = it.id,
                    vehicleLabel = null,
                    ts = Instant.ofEpochMilli(it.timestamp),
                    lat = it.lat,
                    lon = it.lng,
                    bearing = it.bearing,
                    speedMps = it.speed.kmphToMps(), // convert speed in kmph in mps
                    descRouteId = it.routeId,
                    descTripId = it.tripId,
                    descDirectionId = it.directionId.toInt(),
                )
            } ?: emptyList()
    }
}
