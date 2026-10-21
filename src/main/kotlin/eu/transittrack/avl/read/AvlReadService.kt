package eu.transittrack.avl.read

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.avl.read.dto.AvlFeedDto
import eu.transittrack.avl.read.dto.AvlReportCatalogDto
import eu.transittrack.avl.read.dto.AvlReportDto
import eu.transittrack.avl.read.dto.AvlReportMatchDto
import eu.transittrack.avl.read.dto.AvlReportPageDto
import eu.transittrack.avl.read.dto.VehicleDto

@Service
class AvlReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val reports: AvlReportRowRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val matchContextFactory: AvlMatchContextFactory,
) {
    fun avlFeeds(): List<AvlFeedDto> = avlFeeds.findAll().map(AvlFeedDto::of)

    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<VehicleDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        return vehicleStates
            .findByFeedCodeOrderByUpdatedAtDesc(feed.code)
            .filter { it.reportTs.isAfter(cutoff) }
            .filter { !matchedOnly || it.matched }
            .map { VehicleDto.of(it, feed.gtfsFeedCode) }
    }

    fun vehicle(
        feedCode: String,
        vehicleId: String,
    ): VehicleDto? {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        return vehicleStates
            .findByFeedCodeAndVehicleId(feed.code, vehicleId)
            ?.takeIf { it.reportTs.isAfter(cutoff) }
            ?.let { VehicleDto.of(it, feed.gtfsFeedCode) }
    }

    private companion object {
        val MAX_REPORT_AGE: Duration = Duration.ofHours(1)
    }

    fun avlReports(
        feedCode: String,
        vehicleId: String,
        since: String?,
        limit: Int,
    ): List<AvlReportDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val pageable = PageRequest.of(0, limit.coerceIn(1, 1000))
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
        return rows.map { r ->
            AvlReportDto.of(
                r,
                matchesByReportId[r.id]?.let { AvlReportMatchDto.of(it, feed.gtfsFeedCode) },
                feed.format.name,
            )
        }
    }

    fun avlReportCatalog(feedCode: String): AvlReportCatalogDto {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        return AvlReportCatalogDto(
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
    ): AvlReportPageDto {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val zone = reportTimeZone(feed)
        val localDate = LocalDate.parse(date)
        val from = localDate.atTime(LocalTime.parse(startTime)).atZone(zone).toInstant()
        val until = localDate.atTime(LocalTime.parse(endTime)).atZone(zone).toInstant()
        require(until.isAfter(from)) { "end time must be after start time" }

        val requestedPage = page.coerceAtLeast(1)
        val requestedPageSize = pageSize.coerceIn(1, 100)
        val rows =
            reports.findReportPage(
                feed.code,
                vehicleId,
                from,
                until,
                PageRequest.of(requestedPage - 1, requestedPageSize),
            )
        val matchesByReportId =
            vehicleMatches
                .findByAvlReportIdIn(rows.content.mapNotNull { it.id })
                .associateBy { it.avlReportId }
        return AvlReportPageDto(
            items = rows.content.map { row ->
                AvlReportDto.of(
                    row,
                    matchesByReportId[row.id]?.let { AvlReportMatchDto.of(it, feed.gtfsFeedCode) },
                    feed.format.name,
                )
            },
            totalCount = rows.totalElements.toInt(),
            page = requestedPage,
            pageSize = requestedPageSize,
        )
    }

    private fun reportTimeZone(feed: eu.transittrack.avl.model.AvlFeed): ZoneId =
        matchContextFactory.open(feed)?.zone ?: ZoneId.systemDefault()
}
