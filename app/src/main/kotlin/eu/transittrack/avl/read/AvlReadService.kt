package eu.transittrack.avl.read

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

import org.springframework.stereotype.Service

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleState
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.rest.generated.model.AvlAssignmentMode
import eu.transittrack.rest.generated.model.AvlFeed
import eu.transittrack.rest.generated.model.AvlFormat
import eu.transittrack.rest.generated.model.AvlOccupancyStatus
import eu.transittrack.rest.generated.model.AvlReport
import eu.transittrack.rest.generated.model.AvlReportCatalog
import eu.transittrack.rest.generated.model.AvlReportMatch
import eu.transittrack.rest.generated.model.AvlReportPage
import eu.transittrack.rest.generated.model.LatLon
import eu.transittrack.rest.generated.model.MatchStatus
import eu.transittrack.rest.generated.model.PredictionAlgorithm
import eu.transittrack.rest.generated.model.Vehicle
import eu.transittrack.rest.generated.model.VehicleTrail
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.util.paging.PageRequest

@Service
class AvlReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val reports: AvlReportRowRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val matchContextFactory: AvlMatchContextFactory,
    private val trips: TripRepository,
    private val stopPaths: StopPathRepository,
) {
    fun avlFeeds(): List<AvlFeed> = avlFeeds.findAll().map(::toApi)

    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<Vehicle> = toApi(vehicleStates(feedCode, matchedOnly))

    /** The live (recently reported) vehicle states of an AVL feed, newest first. */
    fun vehicleStates(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<VehicleState> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        return vehicleStates
            .findByFeedCodeOrderByUpdatedAtDesc(feed.code)
            .filter { it.reportTs.isAfter(cutoff) }
            .filter { !matchedOnly || it.matched }
    }

    fun vehicle(
        feedCode: String,
        vehicleId: String,
    ): Vehicle? {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        return vehicleStates
            .findByFeedCodeAndVehicleId(feed.code, vehicleId)
            ?.takeIf { it.reportTs.isAfter(cutoff) }
            ?.let { toApi(listOf(it)).single() }
    }

    private companion object {
        val MAX_REPORT_AGE: Duration = Duration.ofHours(1)
    }

    fun avlReports(
        feedCode: String,
        vehicleId: String,
        since: String?,
        limit: Int,
    ): List<AvlReport> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val pageable = PageRequest(0, limit.coerceIn(1, 1000))
        val sinceInstant = since?.let { Instant.parse(it) }
        val rows =
            if (sinceInstant != null) {
                reports.findByVehicleSince(
                    feed.code,
                    vehicleId,
                    sinceInstant,
                    pageable,
                )
            } else {
                reports.findByVehicle(feed.code, vehicleId, pageable)
            }
        // One bulk fetch over the same window rather than resolving each report's match
        // individually — a report has at most one VehicleMatch (keyed by avl_report_id).
        val matchesByReportId =
            (
                if (sinceInstant != null) {
                    vehicleMatches.findByVehicleSince(
                        feed.code,
                        vehicleId,
                        sinceInstant,
                        pageable,
                    )
                } else {
                    vehicleMatches.findByVehicle(feed.code, vehicleId, pageable)
                }
            ).associateBy { it.avlReportId }
        return toApi(rows, matchesByReportId, feed.format)
    }

    /** See [buildVehicleTrail]: the newest [limit] reports, summarised for the vehicle page. */
    fun vehicleTrail(
        feedCode: String,
        vehicleId: String,
        limit: Int,
    ): VehicleTrail {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val rows = reports.findByVehicle(feed.code, vehicleId, PageRequest(0, limit.coerceIn(1, 1000)))
        val matchesByReportId = vehicleMatches.findByAvlReportIdIn(rows.mapNotNull { it.id }).associateBy { it.avlReportId }
        val currentTripRowId = vehicleStates.findByFeedCodeAndVehicleId(feed.code, vehicleId)?.tripRowId
        return buildVehicleTrail(rows, matchesByReportId, currentTripRowId)
    }

    fun avlReportCatalog(feedCode: String): AvlReportCatalog {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        return AvlReportCatalog(
            timeZone = reportTimeZone(feed).id,
            vehicleIds = reports.findVehicleIds(feed.code),
        )
    }

    fun avlReportPage(
        feedCode: String,
        vehicleId: String,
        date: String,
        startTime: String,
        endTime: String,
        page: Int,
        pageSize: Int,
    ): AvlReportPage {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val zone = reportTimeZone(feed)
        val localDate = LocalDate.parse(date)
        val localStart = localDate.atTime(LocalTime.parse(startTime))
        val localEnd = localDate.atTime(LocalTime.parse(endTime))
        require(localEnd.isAfter(localStart)) { "end time must be after start time" }
        val from = resolveAgencyLocalTime(localStart, zone, "start time")
        val until = resolveAgencyLocalTime(localEnd, zone, "end time")

        val requestedPage = page.coerceAtLeast(1)
        val requestedPageSize = pageSize.coerceIn(1, 100)
        val rows =
            reports.findReportPage(
                feed.code,
                vehicleId,
                from,
                until,
                PageRequest(requestedPage - 1, requestedPageSize),
            )
        val matchesByReportId =
            vehicleMatches
                .findByAvlReportIdIn(rows.items.mapNotNull { it.id })
                .associateBy { it.avlReportId }
        return AvlReportPage(
            items = toApi(rows.items, matchesByReportId, feed.format),
            totalCount = rows.total.toInt(),
            page = requestedPage,
            pageSize = requestedPageSize,
        )
    }

    private fun reportTimeZone(feed: eu.transittrack.avl.model.AvlFeed): ZoneId =
        matchContextFactory.open(feed)?.zone ?: ZoneId.systemDefault()

    private fun resolveAgencyLocalTime(
        localTime: java.time.LocalDateTime,
        zone: ZoneId,
        label: String,
    ): Instant {
        val offsets = zone.rules.getValidOffsets(localTime)
        require(offsets.size == 1) {
            if (offsets.isEmpty()) {
                "$label is not a valid agency-local time"
            } else {
                "$label is ambiguous in the agency time zone"
            }
        }
        return localTime.atOffset(offsets.single()).toInstant()
    }

    private fun toApi(f: eu.transittrack.avl.model.AvlFeed) =
        AvlFeed(
            code = f.code,
            name = f.name,
            gtfsFeedCode = f.gtfsFeedCode,
            format = AvlFormat.valueOf(f.format.name),
            pollIntervalSec = f.pollIntervalSec,
            assignmentMode = AvlAssignmentMode.valueOf(f.assignmentMode.name),
            predictionAlgorithms = f.predictionAlgorithms.map { PredictionAlgorithm.valueOf(it.name) },
            enabled = f.enabled,
            lastPollAt = f.lastPollAt?.toString(),
            lastPollStatus = f.lastPollStatus,
            lastPollReportCount = f.lastPollReportCount,
        )

    /** Maps [states], resolving each one's trip and current stop ids in batched lookups. */
    private fun toApi(states: List<VehicleState>): List<Vehicle> {
        val tripIds = trips
            .findAllById(states.mapNotNull { it.tripRowId }.distinct())
            .associate { it.id to it.tripId }
        val stopIdsByPattern = mutableMapOf<Pair<Long, Long>, List<String>>()

        return states.map { s ->
            val revision = s.revisionId
            val pattern = s.tripPatternId
            val index = s.stopPathIndex
            val currentStopId =
                if (revision == null || pattern == null || index == null) {
                    null
                } else {
                    stopIdsByPattern
                        .getOrPut(revision to pattern) {
                            stopPaths.findByTripPatternOrdered(revision, pattern).map { it.stopId }
                        }.getOrNull(index)
                }
            Vehicle(
                vehicleId = s.vehicleId,
                reportTs = s.reportTs.toString(),
                position = LatLon(s.lat, s.lon),
                matched = s.matched,
                stale = s.stale,
                label = s.vehicleLabel,
                snappedPosition = if (s.snappedLat != null && s.snappedLon != null) LatLon(s.snappedLat!!, s.snappedLon!!) else null,
                bearing = s.bearing,
                speedMps = s.speedMps,
                occupancyStatus = s.occupancyStatus?.let { occupancy(it) },
                scheduleAdherenceSec = s.scheduleAdherenceSec,
                stopPathIndex = s.stopPathIndex,
                distanceAlongTripM = s.distanceAlongTripM,
                revisionId = revision?.toString(),
                tripId = s.tripRowId?.let(tripIds::get),
                currentStopId = currentStopId,
            )
        }
    }

    private fun toApi(
        rows: List<AvlReportRow>,
        matchesByReportId: Map<Long, VehicleMatch>,
        source: eu.transittrack.avl.ingest.AvlFormat,
    ): List<AvlReport> {
        val tripIds = trips.findAllById(matchesByReportId.values.map { it.tripRowId }.distinct()).associate { it.id to it.tripId }
        return rows.map { r ->
            AvlReport(
                vehicleId = r.vehicleId,
                ts = r.ts.toString(),
                processedAt = r.createdAt.toString(),
                source = AvlFormat.valueOf(source.name),
                position = LatLon(r.lat, r.lon),
                matchStatus = MatchStatus.valueOf(r.matchStatus.name),
                bearing = r.bearing,
                speedMps = r.speedMps,
                currentStatus = stopStatusOf(r.currentStatus),
                occupancyStatus = r.occupancyStatus?.let { occupancy(it) },
                descTripId = r.descTripId,
                match = matchesByReportId[r.id]?.let { m ->
                    AvlReportMatch(
                        stopPathIndex = m.stopPathIndex,
                        distanceAlongTripM = m.distanceAlongTripM,
                        deviationM = m.deviationM,
                        snappedPosition = LatLon(m.snappedLat, m.snappedLon),
                        revisionId = m.revisionId.toString(),
                        tripId = tripIds.getValue(m.tripRowId),
                        scheduleAdherenceSec = m.scheduleAdherenceSec,
                        heading = m.heading,
                        score = m.score,
                    )
                },
            )
        }
    }

    private fun occupancy(ordinal: Int): AvlOccupancyStatus? =
        runCatching { eu.transittrack.avl.ingest.AvlOccupancyStatus.entries[ordinal] }
            .getOrNull()
            ?.let { AvlOccupancyStatus.valueOf(it.name) }
}
