package eu.transittrack.avl.ingest

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.TripDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.model.AvlFeed

/**
 * Decodes a GTFS-realtime `FeedMessage` into normalized [AvlReport]s, one per entity that carries a
 * `VehiclePosition`. `TripUpdate` / `Alert` entities are ignored (future decoder beans). An entity
 * without a resolvable vehicle id or without a lat/lon is dropped with a warning rather than failing
 * the whole poll.
 */
@Component
class GtfsRealtimeVehiclePositionDecoder : AvlFeedDecoder {
    private val log = LoggerFactory.getLogger(javaClass)
    override val format = AvlFormat.GTFS_RT

    override fun decode(
        payload: RawAvlPayload,
        feed: AvlFeed,
    ): List<AvlReport> {
        val message =
            try {
                FeedMessage.parseFrom(payload.bytes)
            } catch (e: InvalidProtocolBufferException) {
                throw AvlDecodeException("feed '${feed.code}' payload is not a valid GTFS-RT FeedMessage", e)
            }
        val headerTs = message.header.takeIf { it.hasTimestamp() && it.timestamp > 0 }?.timestamp
        return message.entityList.mapNotNull { entity ->
            if (!entity.hasVehicle()) return@mapNotNull null
            toReport(entity, headerTs, payload.fetchedAt, feed)
        }
    }

    private fun toReport(
        fe: GtfsRealtime.FeedEntity,
        headerTs: Long?,
        fetchedAt: Instant,
        feed: AvlFeed,
    ): AvlReport? {
        val vp = fe.vehicle
        val vehicleId = vp.vehicle.id.ifEmpty { vp.vehicle.label }
        if (vehicleId.isEmpty()) {
            log.warn("avl feed '{}': vehicle entity without id or label, skipped", feed.code)
            return null
        }
        if (!vp.hasPosition() || !vp.position.hasLatitude() || !vp.position.hasLongitude()) {
            log.warn("avl feed '{}': vehicle '{}' has no position, skipped", feed.code, vehicleId)
            return null
        }
        val ts =
            when {
                vp.hasTimestamp() && vp.timestamp > 0 -> Instant.ofEpochSecond(vp.timestamp)
                headerTs != null -> Instant.ofEpochSecond(headerTs)
                else -> fetchedAt
            }
        val pos = vp.position
        val trip = if (vp.hasTrip()) vp.trip else null
        return AvlReport(
            vehicleId = vehicleId,
            vehicleLabel = vp.vehicle.label.ifEmpty { null },
            ts = ts,
            lat = pos.latitude.toDouble(),
            lon = pos.longitude.toDouble(),
            bearing = if (pos.hasBearing()) pos.bearing.toDouble() else null,
            speedMps = if (pos.hasSpeed()) pos.speed.toDouble() else null,
            odometerM = if (pos.hasOdometer()) pos.odometer else null,
            descTripId = trip?.tripId?.ifEmpty { null },
            descRouteId = trip?.routeId?.ifEmpty { null },
            descDirectionId = if (trip != null && trip.hasDirectionId()) trip.directionId else null,
            descStartDate = trip?.startDate?.ifEmpty { null }?.let(::parseStartDate),
            descStartTimeSec = trip?.startTime?.ifEmpty { null }?.let(::parseGtfsTime),
            descScheduleRelationship =
                trip?.takeIf { it.hasScheduleRelationship() }?.scheduleRelationship?.let(::mapRelationship),
            currentStopSequence = if (vp.hasCurrentStopSequence()) vp.currentStopSequence else null,
            currentStopId = vp.stopId.ifEmpty { null },
            currentStatus = if (vp.hasCurrentStatus()) mapStopStatus(vp.currentStatus) else null,
            occupancyStatus = if (vp.hasOccupancyStatus()) mapOccupancy(vp.occupancyStatus) else null,
            congestionLevel = if (vp.hasCongestionLevel()) mapCongestion(vp.congestionLevel) else null,
        )
    }

    private fun parseStartDate(s: String): LocalDate? = runCatching { LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

    /** GTFS `HH:mm:ss`, hours may exceed 23. */
    private fun parseGtfsTime(s: String): Int? {
        val parts = s.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val sec = parts[2].toIntOrNull() ?: return null
        return h * 3600 + m * 60 + sec
    }

    private fun mapRelationship(r: TripDescriptor.ScheduleRelationship): RtScheduleRelationship? =
        when (r) {
            TripDescriptor.ScheduleRelationship.SCHEDULED -> RtScheduleRelationship.SCHEDULED
            TripDescriptor.ScheduleRelationship.ADDED -> RtScheduleRelationship.ADDED
            TripDescriptor.ScheduleRelationship.UNSCHEDULED -> RtScheduleRelationship.UNSCHEDULED
            TripDescriptor.ScheduleRelationship.CANCELED -> RtScheduleRelationship.CANCELED
            TripDescriptor.ScheduleRelationship.DUPLICATED -> RtScheduleRelationship.DUPLICATED
            else -> null
        }

    private fun mapStopStatus(s: VehiclePosition.VehicleStopStatus): VehicleStopStatus? =
        when (s) {
            VehiclePosition.VehicleStopStatus.INCOMING_AT -> VehicleStopStatus.INCOMING_AT
            VehiclePosition.VehicleStopStatus.STOPPED_AT -> VehicleStopStatus.STOPPED_AT
            VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO -> VehicleStopStatus.IN_TRANSIT_TO
            else -> null
        }

    private fun mapOccupancy(o: VehiclePosition.OccupancyStatus): AvlOccupancyStatus? =
        runCatching { AvlOccupancyStatus.valueOf(o.name) }.getOrNull()

    private fun mapCongestion(c: VehiclePosition.CongestionLevel): AvlCongestionLevel? =
        runCatching { AvlCongestionLevel.valueOf(c.name) }.getOrNull()
}
