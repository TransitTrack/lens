package eu.transittrack.reports.api

import java.time.LocalDate
import java.time.format.DateTimeParseException

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.reports.read.AdherenceReadService
import eu.transittrack.rest.generated.api.ReportsApi
import eu.transittrack.rest.generated.model.AvlOccupancyStatus
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.RouteAdherenceSummary
import eu.transittrack.rest.generated.model.StopPassage
import eu.transittrack.rest.generated.model.TripAdherenceSummary

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class ReportsRestController(
    private val read: AdherenceReadService,
) : ReportsApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun handleInvalidRequest(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Bad Request", status = ex.statusCode.value(), detail = ex.reason))

    private fun <T, R> page(
        offset: Int,
        limit: Int,
        startDate: String,
        endDate: String,
        get: () -> List<T>,
        map: (T) -> R,
    ): ResponseEntity<List<R>> {
        if (offset < 0 || limit !in 1..500) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offset or limit")
        }
        try {
            if (LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date")
            }
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must be in ISO-8601 format", ex)
        }
        val all = get()
        return ResponseEntity.ok().header("X-Total-Count", all.size.toString()).body(all.drop(offset).take(limit).map(map))
    }

    override fun allRoutesAdherence(
        feedCode: String,
        startDate: String,
        endDate: String,
        offset: Int,
        limit: Int,
    ) = page(offset, limit, startDate, endDate, { read.allRoutesAdherence(feedCode, startDate, endDate) }) {
        RouteAdherenceSummary(it.routeId, it.sampleCount, it.meanAdherenceSec, it.onTimePct, it.routeShortName)
    }

    override fun allTripsAdherence(
        feedCode: String,
        startDate: String,
        endDate: String,
        routeId: String?,
        offset: Int,
        limit: Int,
    ) = page(offset, limit, startDate, endDate, { read.allTripsAdherence(feedCode, routeId, startDate, endDate) }) {
        TripAdherenceSummary(it.tripId, it.routeId, it.serviceDate.toString(), it.sampleCount, it.meanAdherenceSec, it.onTimePct)
    }

    override fun routeAdherence(
        feedCode: String,
        routeId: String,
        startDate: String,
        endDate: String,
        offset: Int,
        limit: Int,
    ) = page(offset, limit, startDate, endDate, { read.routeAdherence(feedCode, routeId, startDate, endDate) }, ::passage)

    override fun tripAdherence(
        feedCode: String,
        tripId: String,
        startDate: String,
        endDate: String,
        offset: Int,
        limit: Int,
    ) = page(offset, limit, startDate, endDate, { read.tripAdherence(feedCode, tripId, startDate, endDate) }, ::passage)

    override fun stopAdherence(
        feedCode: String,
        stopId: String,
        startDate: String,
        endDate: String,
        offset: Int,
        limit: Int,
    ) = page(offset, limit, startDate, endDate, { read.stopAdherence(feedCode, stopId, startDate, endDate) }, ::passage)

    private fun passage(p: eu.transittrack.reports.model.StopPassage) =
        StopPassage(
            tripId = p.tripId, routeId = p.routeId, serviceDate = p.serviceDate.toString(), stopId = p.stopId,
            stopPathIndex = p.stopPathIndex, vehicleId = p.vehicleId, blockId = p.blockId, stopName = p.stopName,
            scheduledSec = p.scheduledSec, observedSec = p.observedSec, adherenceSec = p.adherenceSec,
            direction = p.direction, occupancyStatus = p.occupancyStatus?.let { AvlOccupancyStatus.valueOf(it.name) },
        )
}
