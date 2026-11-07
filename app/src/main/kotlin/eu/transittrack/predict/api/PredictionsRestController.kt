package eu.transittrack.predict.api

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.predict.learn.DayType
import eu.transittrack.predict.learn.TimeBand
import eu.transittrack.predict.read.HeadwayReadService
import eu.transittrack.predict.read.PredictionReadService
import eu.transittrack.rest.generated.api.PredictionsApi
import eu.transittrack.rest.generated.model.Headway
import eu.transittrack.rest.generated.model.PredictionAccuracySummary
import eu.transittrack.rest.generated.model.PredictionAlgorithm
import eu.transittrack.rest.generated.model.PredictionDayType
import eu.transittrack.rest.generated.model.PredictionTimeBand
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.StopPrediction

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class PredictionsRestController(
    private val read: PredictionReadService,
    private val headways: HeadwayReadService,
) : PredictionsApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun error(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Bad Request", status = ex.statusCode.value(), detail = ex.reason))

    private fun <T, R> page(
        offset: Int,
        limit: Int,
        get: () -> List<T>,
        convert: (T) -> R,
    ): ResponseEntity<List<R>> {
        if (offset < 0 || limit !in 1..500) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offset or limit")
        val rows = get()
        return ResponseEntity.ok().header("X-Total-Count", rows.size.toString()).body(rows.drop(offset).take(limit).map(convert))
    }

    override fun headway(
        feedCode: String,
        stopId: String,
        routeId: String,
        directionId: Int?,
        algorithm: PredictionAlgorithm?,
    ): ResponseEntity<Headway> {
        val x = headways
            .headway(
                feedCode, stopId, routeId, directionId,
                algorithm?.let {
                    eu.transittrack.predict.PredictionAlgorithm
                        .valueOf(it.name)
                },
            )
        return ResponseEntity.ok(x)
    }

    override fun vehiclePredictions(
        feedCode: String,
        vehicleId: String,
        algorithm: PredictionAlgorithm?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<StopPrediction>> =
        page(offset, limit, {
            read
                .vehiclePredictions(
                    feedCode, vehicleId,
                    algorithm?.let {
                        eu.transittrack.predict.PredictionAlgorithm
                            .valueOf(it.name)
                    },
                )
        }) { it }

    override fun stopPredictions(
        feedCode: String,
        stopId: String,
        routeId: String?,
        directionId: Int?,
        algorithm: PredictionAlgorithm?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<StopPrediction>> =
        page(offset, limit, {
            read
                .stopPredictions(
                    feedCode, stopId, routeId, directionId,
                    algorithm?.let {
                        eu.transittrack.predict.PredictionAlgorithm
                            .valueOf(it.name)
                    },
                )
        }) { it }

    override fun predictionAccuracy(
        feedCode: String,
        algorithm: PredictionAlgorithm?,
        sinceDays: Int,
        routeId: String?,
        directionId: Int?,
        dayType: PredictionDayType?,
        timeBand: PredictionTimeBand?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<PredictionAccuracySummary>> {
        if (sinceDays <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sinceDays must be positive")
        return page(offset, limit, {
            read
                .predictionAccuracy(
                    feedCode,
                    algorithm?.let {
                        eu.transittrack.predict.PredictionAlgorithm
                            .valueOf(it.name)
                    },
                    sinceDays, routeId, directionId, dayType?.let { DayType.valueOf(it.name) },
                    timeBand?.let {
                        TimeBand
                            .valueOf(it.name)
                    },
                )
        }) { it }
    }
}
