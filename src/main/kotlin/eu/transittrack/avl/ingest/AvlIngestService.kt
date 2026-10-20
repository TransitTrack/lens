package eu.transittrack.avl.ingest

import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.AvlFormat
import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.feed.AvlFeedSource
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.MatchStatus
import eu.transittrack.observability.TransitTrackMetrics

/**
 * One poll cycle for one AVL feed: fetch -> decode -> drop reports older than
 * `avl.ingest.maxReportAgeHours` -> drop `(vehicleId, ts)` duplicates already stored -> batch-insert
 * the survivors as PENDING `avl_report` rows -> record `last_poll_*`. No matching happens here;
 * `AvlMatchProcessor` consumes the PENDING rows asynchronously.
 */
@Service
class AvlIngestService(
    private val source: AvlFeedSource,
    decoders: ObjectProvider<AvlFeedDecoder>,
    private val writer: AvlWriter,
    private val reports: AvlReportRowRepository,
    private val pollRecorder: AvlPollRecorder,
    private val props: AvlProperties = AvlProperties(),
    private val metrics: TransitTrackMetrics = TransitTrackMetrics.forTests(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val decoders: Map<AvlFormat, AvlFeedDecoder> = decoders.associateBy { it.format }

    @Transactional
    fun pollOnce(feed: AvlFeed): Int {
        val startedAt = Instant.now()
        val decoder =
            decoders[feed.format]
                ?: error("no AvlFeedDecoder for format ${feed.format} (feed '${feed.code}')")
        val payload =
            try {
                source.fetch(feed)
            } catch (e: Exception) {
                metrics
                    .avlPollFinished(
                        feed.code, feed.format.name.lowercase(), TransitTrackMetrics.Outcome.FAILED,
                        Duration
                            .between(startedAt, Instant.now()),
                    )
                pollRecorder.recordPoll(feed.id!!, status = "ERR: ${e.message?.take(200)}", count = 0)
                throw e
            }
        val rawDecoded =
            try {
                decoder.decode(payload, feed.toDescriptor())
            } catch (e: Exception) {
                metrics
                    .avlPollFinished(
                        feed.code, feed.format.name.lowercase(), TransitTrackMetrics.Outcome.FAILED,
                        Duration
                            .between(startedAt, Instant.now()),
                    )
                pollRecorder.recordPoll(feed.id!!, status = "ERR: ${e.message?.take(200)}", count = 0)
                throw e
            }

        // Reject malformed coordinates as early as possible, in one place, so every decoder — built
        // in or third-party — is covered: a NaN/infinite/out-of-range lat or lon can both throw deep
        // in the geodetic math (GeodeticCalculator) and silently produce a NaN deviation that the
        // matcher would otherwise treat as a distance-0 match. Dropping it here means it never
        // becomes a PENDING `avl_report` row, so it can never become a poison pill that re-throws on
        // every match tick.
        val (malformed, decoded) = rawDecoded.partition { !it.hasValidCoordinates() }
        if (malformed.isNotEmpty()) {
            log.warn(
                "avl feed '{}': dropped {} report(s) with invalid lat/lon (vehicle ids: {})",
                feed.code, malformed.size, malformed.map { it.vehicleId }.distinct(),
            )
        }

        // Relative to the poll's own fetch time rather than wall-clock now(), so a slow poll or a
        // feed clock skewed from ours doesn't itself make otherwise-fresh reports look stale.
        val cutoff = payload.fetchedAt.minus(props.ingest.maxReportAgeHours)
        val (stale, current) = decoded.partition { it.ts.isBefore(cutoff) }
        if (stale.isNotEmpty()) {
            log.warn(
                "avl feed '{}': dropped {} report(s) older than {} (oldest ts={})",
                feed.code, stale.size, cutoff, stale.minOf { it.ts },
            )
        }

        val latest = reports.latestTsByVehicle(feed.code).associate { it.vehicleId to it.ts }
        val fresh = current.filter { latest[it.vehicleId]?.isBefore(it.ts) ?: true }
        writer.insertReports(fresh.map { toRow(feed.code, it) })
        pollRecorder.recordPoll(feed.id!!, status = "OK", count = fresh.size)
        metrics.avlReports(feed.code, feed.format.name.lowercase(), rawDecoded.size, fresh.size, stale.size, malformed.size)
        metrics
            .avlPollFinished(
                feed.code, feed.format.name.lowercase(), TransitTrackMetrics.Outcome.SUCCESS,
                Duration
                    .between(startedAt, Instant.now()),
            )
        log.trace(
            "avl feed '{}': {} decoded, {} invalid, {} new, {} stale",
            feed.code, rawDecoded.size, malformed.size, fresh.size, stale.size,
        )
        return fresh.size
    }

    private fun AvlFeed.toDescriptor() = FeedDescriptor(code, name, url, format, headers)

    private fun toRow(
        feedCode: String,
        r: AvlReport,
    ) = AvlReportRow(
        feedCode = feedCode,
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

/**
 * `false` for a NaN/infinite lat or lon, or one outside the physically valid range (`|lat| > 90`,
 * `|lon| > 180`). The single gate every decoder's output passes through — built-in or third-party
 * — before a report can become a PENDING `avl_report` row.
 */
private fun AvlReport.hasValidCoordinates(): Boolean =
    lat.isFinite() && lon.isFinite() && lat.absoluteValue <= 90.0 && lon.absoluteValue <= 180.0
