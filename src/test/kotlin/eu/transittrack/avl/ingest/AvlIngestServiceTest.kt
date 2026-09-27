package eu.transittrack.avl.ingest

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedHeader
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtime.Position
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.AvlFeedSource
import eu.transittrack.avl.feed.RawAvlPayload
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

/**
 * [AvlWriter] commits through its own stateless-session transaction on a separate connection, so this
 * test must NOT run inside the default rollback transaction — the seeded `avl_feed` parent of the
 * `avl_report.feed_id` FK must be committed and visible. Hence `NOT_SUPPORTED` + explicit cleanup.
 */
@PostgresSliceTest
@Import(AvlWriter::class, AvlPollRecorder::class, GtfsRealtimeVehiclePositionDecoder::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AvlIngestServiceTest(
    @Autowired val feeds: AvlFeedRepository,
    @Autowired val reports: AvlReportRowRepository,
    @Autowired val writer: AvlWriter,
    @Autowired val decoders: ObjectProvider<AvlFeedDecoder>,
    @Autowired val pollRecorder: AvlPollRecorder,
) : PostgresPerMethodTest() {
    private var bytes: ByteArray = ByteArray(0)
    private val source =
        object : AvlFeedSource {
            override fun fetch(feed: AvlFeed) = RawAvlPayload(bytes, null, Instant.parse("2026-09-04T10:00:00Z"))
        }
    private val service by lazy { AvlIngestService(source, decoders, writer, reports, pollRecorder) }

    @AfterEach
    fun cleanup() {
        reports.deleteAll()
        feeds.findByCode("f")?.id?.let { feeds.deleteById(it) }
    }

    private fun message(
        ts: Long,
        vararg vehicles: Pair<String, Long>,
    ): ByteArray =
        FeedMessage
            .newBuilder()
            .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(ts))
            .addAllEntity(
                vehicles.mapIndexed { i, (v, vts) ->
                    FeedEntity
                        .newBuilder()
                        .setId("e$i")
                        .setVehicle(
                            VehiclePosition
                                .newBuilder()
                                .setVehicle(VehicleDescriptor.newBuilder().setId(v))
                                .setPosition(Position.newBuilder().setLatitude(45.75f).setLongitude(21.22f))
                                .setTimestamp(vts),
                        ).build()
                },
            ).build()
            .toByteArray()

    private fun feed() =
        feeds.save(
            AvlFeed(
                code = "f", name = "F", gtfsFeedCode = "g", url = "https://x.test/vp.pb", format = AvlFormat.GTFS_RT,
                pollIntervalSec = 15, assignmentMode = AvlAssignmentMode.FULL_INFERENCE, enabled = true, headers = null,
                source = AvlFeedSourceKind.CONFIG, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )

    /** The fixed `fetchedAt` the fake [source] returns (2026-09-04T10:00:00Z), as epoch seconds. */
    private val fetchedAtEpoch = 1_788_516_000L

    @Test
    fun `reports older than the max age are dropped, fresh ones in the same poll still insert`() {
        val f = feed()
        val threeHoursBeforeFetch = fetchedAtEpoch - 3 * 3_600
        bytes = message(fetchedAtEpoch, "old" to (threeHoursBeforeFetch - 1), "fresh" to fetchedAtEpoch)

        assertThat(service.pollOnce(f)).isEqualTo(1)
        assertThat(reports.count()).isEqualTo(1L)
        assertThat(reports.findAll().single().vehicleId).isEqualTo("fresh")
    }

    @Test
    fun `first poll inserts, second poll dedupes unchanged`() {
        val f = feed()
        bytes = message(fetchedAtEpoch, "a" to fetchedAtEpoch, "b" to fetchedAtEpoch)
        assertThat(service.pollOnce(f)).isEqualTo(2)

        // same timestamps -> nothing new
        assertThat(service.pollOnce(f)).isEqualTo(0)

        // b moves on -> one new row
        bytes = message(fetchedAtEpoch + 30, "a" to fetchedAtEpoch, "b" to fetchedAtEpoch + 30)
        assertThat(service.pollOnce(f)).isEqualTo(1)
        assertThat(reports.count()).isEqualTo(3L)

        val reloaded = feeds.findByCode("f")!!
        assertThat(reloaded.lastPollStatus).isEqualTo("OK")
        assertThat(reloaded.lastPollReportCount).isEqualTo(1)
    }
}
