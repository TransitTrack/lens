package eu.transittrack.avl.read

import java.time.Instant

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleStateRepository
import eu.transittrack.avl.read.dto.AvlFeedDto
import eu.transittrack.avl.read.dto.AvlReportDto
import eu.transittrack.avl.read.dto.VehicleDto

@Service
class AvlReadService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleStates: VehicleStateRepository,
    private val reports: AvlReportRowRepository,
) {
    fun avlFeeds(): List<AvlFeedDto> = avlFeeds.findAll().map(AvlFeedDto::of)

    fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
    ): List<VehicleDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        return vehicleStates
            .findByFeedIdOrderByUpdatedAtDesc(feed.id!!)
            .filter { !matchedOnly || it.matched }
            .map { VehicleDto.of(it, feed.gtfsFeedCode) }
    }

    fun vehicle(
        feedCode: String,
        vehicleId: String,
    ): VehicleDto? {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        return vehicleStates
            .findByFeedIdAndVehicleId(feed.id!!, vehicleId)
            ?.let { VehicleDto.of(it, feed.gtfsFeedCode) }
    }

    fun avlReports(
        feedCode: String,
        vehicleId: String,
        since: String?,
        limit: Int,
    ): List<AvlReportDto> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val pageable = PageRequest.of(0, limit.coerceIn(1, 1000))
        val rows =
            if (since != null) {
                reports.findByFeedIdAndVehicleIdAndTsGreaterThanEqualOrderByTsDesc(
                    feed.id!!,
                    vehicleId,
                    Instant.parse(since),
                    pageable,
                )
            } else {
                reports.findByFeedIdAndVehicleIdOrderByTsDesc(feed.id!!, vehicleId, pageable)
            }
        return rows.map(AvlReportDto::of)
    }
}
