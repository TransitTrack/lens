package eu.transittrack.avl.api

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.read.AvlReadService
import eu.transittrack.rest.found
import eu.transittrack.rest.generated.api.AVLApi
import eu.transittrack.rest.generated.model.AvlReport
import eu.transittrack.rest.generated.model.AvlReportCatalog
import eu.transittrack.rest.generated.model.AvlReportPage
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.Vehicle
import eu.transittrack.rest.generated.model.VehicleTrail
import eu.transittrack.rest.pageOf

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class AvlRestController(
    private val read: AvlReadService,
) : AVLApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun error(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Request failed", status = ex.statusCode.value(), detail = ex.reason))

    override fun vehicles(
        feedCode: String,
        matchedOnly: Boolean,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Vehicle>> = pageOf(offset, limit, { read.vehicles(feedCode, matchedOnly) }) { it }

    override fun vehicle(
        feedCode: String,
        vehicleId: String,
    ): ResponseEntity<Vehicle> = found(read.vehicle(feedCode, vehicleId)) { it }

    override fun vehicleTrail(
        feedCode: String,
        vehicleId: String,
        limit: Int,
    ): ResponseEntity<VehicleTrail> {
        if (limit !in 1..1000) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid limit")
        return ResponseEntity.ok(read.vehicleTrail(feedCode, vehicleId, limit))
    }

    override fun avlReportCatalog(feedCode: String): ResponseEntity<AvlReportCatalog> = ResponseEntity.ok(read.avlReportCatalog(feedCode))

    override fun avlReports(
        feedCode: String,
        vehicleId: String,
        since: String?,
        limit: Int,
    ): ResponseEntity<List<AvlReport>> {
        if (limit !in 1..1000) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid limit")
        return ResponseEntity.ok(read.avlReports(feedCode, vehicleId, since, limit))
    }

    override fun avlReportPage(
        feedCode: String,
        vehicleId: String,
        date: String,
        startTime: String,
        endTime: String,
        page: Int,
        pageSize: Int,
    ): ResponseEntity<AvlReportPage> = ResponseEntity.ok(read.avlReportPage(feedCode, vehicleId, date, startTime, endTime, page, pageSize))
}
