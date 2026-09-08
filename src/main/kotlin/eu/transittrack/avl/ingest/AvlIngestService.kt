package eu.transittrack.avl.ingest

import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlFormat
import eu.transittrack.avl.feed.AvlFeedSource
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus

/**
 * One poll cycle for one AVL feed: fetch -> decode -> drop `(vehicleId, ts)` duplicates already
 * stored -> batch-insert the survivors as PENDING `avl_report` rows -> record `last_poll_*`. No
 * matching happens here; `AvlMatchProcessor` consumes the PENDING rows asynchronously.
 */
@Service
class AvlIngestService(
    private val feeds: AvlFeedRepository,
    private val source: AvlFeedSource,
    decoders: List<AvlFeedDecoder>,
    private val writer: AvlWriter,
    private val reports: AvlReportRowRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val decoders: Map<AvlFormat, AvlFeedDecoder> = decoders.associateBy { it.format }

    @Transactional
    fun pollOnce(feed: AvlFeed): Int {
        val decoder =
            decoders[feed.format]
                ?: error("no AvlFeedDecoder for format ${feed.format} (feed '${feed.code}')")
        val decoded =
            try {
                decoder.decode(source.fetch(feed), feed)
            } catch (e: Exception) {
                recordPoll(feed.id!!, status = "ERR: ${e.message?.take(200)}", count = 0)
                throw e
            }

        val latest = reports.latestTsByVehicle(feed.id!!).associate { it.vehicleId to it.ts }
        val fresh = decoded.filter { latest[it.vehicleId] != it.ts }
        writer.insertReports(fresh.map { toRow(feed.id!!, it) })
        recordPoll(feed.id!!, status = "OK", count = fresh.size)
        log.debug("avl feed '{}': {} decoded, {} new", feed.code, decoded.size, fresh.size)
        return fresh.size
    }

    @Transactional
    fun recordPoll(
        feedId: Long,
        status: String,
        count: Int,
    ) {
        val f = feeds.findById(feedId).orElseThrow()
        f.lastPollAt = Instant.now()
        f.lastPollStatus = status
        f.lastPollReportCount = count
        feeds.save(f)
    }

    private fun toRow(
        feedId: Long,
        r: AvlReport,
    ) = AvlReportRow(
        feedId = feedId,
        vehicleId = r.vehicleId,
        vehicleLabel = r.vehicleLabel,
        ts = r.ts,
        lat = r.lat,
        lon = r.lon,
        bearing = r.bearing,
        speedMps = r.speedMps,
        odometerM = r.odometerM,
        descTripId = r.descTripId,
        descRouteId = r.descRouteId,
        descDirectionId = r.descDirectionId,
        descStartDate = r.descStartDate,
        descStartTimeSec = r.descStartTimeSec,
        descScheduleRelationship = r.descScheduleRelationship?.ordinal,
        currentStopSequence = r.currentStopSequence,
        currentStopId = r.currentStopId,
        currentStatus = r.currentStatus?.ordinal,
        occupancyStatus = r.occupancyStatus?.ordinal,
        congestionLevel = r.congestionLevel?.ordinal,
        matchStatus = MatchStatus.PENDING,
        matchedAt = null,
        createdAt = Instant.now(),
    )
}
