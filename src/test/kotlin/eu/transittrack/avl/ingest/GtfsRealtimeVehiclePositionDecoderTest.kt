package eu.transittrack.avl.ingest

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.TripDescriptor
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition

import eu.transittrack.avl.AvlAssignmentMode
import eu.transittrack.avl.AvlFormat
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedSourceKind

class GtfsRealtimeVehiclePositionDecoderTest {
    private val decoder = GtfsRealtimeVehiclePositionDecoder()
    private val feed = AvlFeed(
        code = "f", name = "F", gtfsFeedCode = "g", url = "https://x.test/vp.pb", format = AvlFormat.GTFS_RT,
        pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = true,
        headers = null, source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun payload(m: FeedMessage) = RawAvlPayload(m.toByteArray(), "application/x-protobuf", Instant.parse("2026-09-04T10:00:00Z"))

    private fun message(vararg entities: FeedEntity): FeedMessage =
        FeedMessage
            .newBuilder()
            .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_756_980_000))
            .addAllEntity(entities.toList())
            .build()

    private fun vpEntity(build: VehiclePosition.Builder.() -> Unit): FeedEntity =
        FeedEntity
            .newBuilder()
            .setId("e")
            .setVehicle(VehiclePosition.newBuilder().apply(build))
            .build()

    @Test
    fun `maps a full VehiclePosition`() {
        val m = message(
            vpEntity {
                vehicle = VehicleDescriptor
                    .newBuilder()
                    .setId("bus-1")
                    .setLabel("101")
                    .build()
                position = Position
                    .newBuilder()
                    .setLatitude(45.75f)
                    .setLongitude(21.22f)
                    .setBearing(90f)
                    .setSpeed(12.5f)
                    .build()
                trip = TripDescriptor
                    .newBuilder()
                    .setTripId("t-1")
                    .setRouteId("r-1")
                    .setDirectionId(1)
                    .setStartDate("20260904")
                    .setStartTime("25:10:00")
                    .setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED)
                    .build()
                timestamp = 1_756_980_030
                currentStopSequence = 4
                stopId = "s-4"
                currentStatus = VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO
                occupancyStatus = VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE
            },
        )
        val r = decoder.decode(payload(m), feed).single()
        assertThat(r.vehicleId).isEqualTo("bus-1")
        assertThat(r.vehicleLabel).isEqualTo("101")
        assertThat(r.ts).isEqualTo(Instant.ofEpochSecond(1_756_980_030))
        assertThat(r.lat).isEqualTo(45.75)
        assertThat(r.bearing).isEqualTo(90.0)
        assertThat(r.descTripId).isEqualTo("t-1")
        assertThat(r.descDirectionId).isEqualTo(1)
        assertThat(r.descStartDate).isEqualTo(LocalDate.of(2026, 9, 4))
        assertThat(r.descStartTimeSec).isEqualTo(25 * 3600 + 600)
        assertThat(r.descScheduleRelationship).isEqualTo(RtScheduleRelationship.SCHEDULED)
        assertThat(r.currentStatus).isEqualTo(VehicleStopStatus.IN_TRANSIT_TO)
        assertThat(r.occupancyStatus).isEqualTo(AvlOccupancyStatus.FEW_SEATS_AVAILABLE)
    }

    @Test
    fun `falls back to header timestamp then fetchedAt`() {
        val r = decoder
            .decode(
                payload(
                    message(
                        vpEntity {
                            vehicle = VehicleDescriptor.newBuilder().setId("v").build()
                            position = Position
                                .newBuilder()
                                .setLatitude(1f)
                                .setLongitude(2f)
                                .build()
                        },
                    ),
                ),
                feed,
            ).single()
        assertThat(r.ts).isEqualTo(Instant.ofEpochSecond(1_756_980_000))
    }

    @Test
    fun `skips entities without a vehicle id or label`() {
        val m = message(
            vpEntity {
                position = Position
                    .newBuilder()
                    .setLatitude(1f)
                    .setLongitude(2f)
                    .build()
            },
        )
        assertThat(decoder.decode(payload(m), feed)).hasSize(0)
    }

    @Test
    fun `skips entities without a position`() {
        val m = message(vpEntity { vehicle = VehicleDescriptor.newBuilder().setId("v").build() })
        assertThat(decoder.decode(payload(m), feed)).hasSize(0)
    }

    @Test
    fun `ignores TripUpdate and Alert entities`() {
        val m = FeedMessage
            .newBuilder()
            .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1))
            .addEntity(
                FeedEntity.newBuilder().setId("tu").setTripUpdate(
                    com.google.transit.realtime.GtfsRealtime.TripUpdate
                        .newBuilder()
                        .setTrip(TripDescriptor.newBuilder().setTripId("t")),
                ),
            ).build()
        assertThat(decoder.decode(payload(m), feed)).hasSize(0)
    }

    @Test
    fun `garbage bytes raise AvlDecodeException`() {
        assertFailure {
            decoder.decode(RawAvlPayload(byteArrayOf(9, 9, 9, 9), null, Instant.EPOCH), feed)
        }.isInstanceOf(AvlDecodeException::class)
    }

    @Test
    fun `missing trip descriptor leaves descriptor fields null`() {
        val r = decoder
            .decode(
                payload(
                    message(
                        vpEntity {
                            vehicle = VehicleDescriptor.newBuilder().setId("v").build()
                            position = Position
                                .newBuilder()
                                .setLatitude(1f)
                                .setLongitude(2f)
                                .build()
                        },
                    ),
                ),
                feed,
            ).single()
        assertThat(r.descTripId).isNull()
        assertThat(r.descStartTimeSec).isNull()
    }
}
