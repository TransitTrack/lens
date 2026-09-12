package eu.transittrack.avl.read

import java.time.Duration
import java.time.Instant

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.avl.read.dto.AvlFeedDto
import eu.transittrack.avl.read.dto.AvlReportDto
import eu.transittrack.avl.read.dto.AvlReportMatchDto
import eu.transittrack.avl.read.dto.VehicleDto

@Service
class AvlReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val reports: AvlReportRowRepository,
    private val vehicleMatches: VehicleMatchRepository,
) {
    fun avlFeeds(): List<AvlFeedDto> = avlFeeds.findAll().map(AvlFeedDto::of)

    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<VehicleDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        return vehicleStates
            .findByFeedIdOrderByUpdatedAtDesc(feed.id!!)
            .filter { !it.reportTs.isBefore(cutoff) }
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
            .findByFeedIdAndVehicleId(feed.id!!, vehicleId)
            ?.takeIf { !it.reportTs.isBefore(cutoff) }
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
                reports.findByFeedIdAndVehicleIdAndTsGreaterThanEqualOrderByTsDesc(
                    feed.id!!,
                    vehicleId,
                    sinceInstant,
                    pageable,
                )
            } else {
                reports.findByFeedIdAndVehicleIdOrderByTsDesc(feed.id!!, vehicleId, pageable)
            }
        // One bulk fetch over the same window rather than resolving each report's match
        // individually — a report has at most one VehicleMatch (keyed by avl_report_id).
        val matchesByReportId =
            (
                if (sinceInstant != null) {
                    vehicleMatches.findByFeedIdAndVehicleIdAndTsGreaterThanEqualOrderByTsDesc(
                        feed.id!!,
                        vehicleId,
                        sinceInstant,
                        pageable,
                    )
                } else {
                    vehicleMatches.findByFeedIdAndVehicleIdOrderByTsDesc(feed.id!!, vehicleId, pageable)
                }
            ).associateBy { it.avlReportId }
        return rows.map { r ->
            AvlReportDto.of(r, matchesByReportId[r.id]?.let { AvlReportMatchDto.of(it, feed.gtfsFeedCode) })
        }
    }
}
