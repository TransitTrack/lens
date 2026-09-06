package eu.transittrack.avl.ingest

import java.time.Instant

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.feed.AvlFormat

private data class StptVehiclePositionResponse(
    val success: Boolean,
    val data: Data,
)

private data class Data(
    val vehicles: List<Vehicle>,
    val total: Long,
    @JsonProperty("generated_at")
    val generatedAt: Long,
    @JsonProperty("server_time")
    val serverTime: Long,
    @JsonProperty("age_seconds")
    val ageSeconds: Long,
    @JsonProperty("is_stale")
    val isStale: Boolean,
    @JsonProperty("service_expected_now")
    val serviceExpectedNow: Boolean,
    @JsonProperty("service_window")
    val serviceWindow: ServiceWindow,
    @JsonProperty("upstream_status")
    val upstreamStatus: String,
    @JsonProperty("upstream_status_reason")
    val upstreamStatusReason: Any?,
    @JsonProperty("live_status")
    val liveStatus: String,
    @JsonProperty("live_status_reason")
    val liveStatusReason: Any?,
    @JsonProperty("newest_vehicle_ts")
    val newestVehicleTs: Long,
    @JsonProperty("vehicle_data_age_seconds")
    val vehicleDataAgeSeconds: Long,
)

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

private data class ServiceWindow(
    @JsonProperty("first_departure")
    val firstDeparture: String,
    @JsonProperty("last_arrival")
    val lastArrival: String,
)

@Component
class StptVehiclePositionDecoder(
    val jsonMapper: JsonMapper,
) : AvlFeedDecoder {
    override val format: AvlFormat
        get() = AvlFormat.STPT

    override fun decode(
        payload: RawAvlPayload,
        feed: AvlFeed,
    ): List<AvlReport> {
        val response = payload.bytes
            .toString(Charsets.UTF_8)
            .let { jsonMapper.readValue(it, StptVehiclePositionResponse::class.java) }
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
                    speedMps = it.speed,
                    descRouteId = it.routeId,
                    descTripId = it.tripId,
                    descDirectionId = it.directionId.toInt(),
                )
            } ?: emptyList()
    }
}
