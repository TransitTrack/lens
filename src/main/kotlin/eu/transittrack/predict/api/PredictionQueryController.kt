package eu.transittrack.predict.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.read.HeadwayReadService
import eu.transittrack.predict.read.PredictionReadService

/**
 * GraphQL query entry points for the prediction read layer. Thin delegation to
 * [PredictionReadService]. Registers unconditionally (only generation/retention are feature-gated).
 */
@Controller
class PredictionQueryController(
    private val read: PredictionReadService,
    private val headwayRead: HeadwayReadService,
) {
    @QueryMapping
    fun vehiclePredictions(
        @Argument feedCode: String,
        @Argument vehicleId: String,
    ) = read.vehiclePredictions(feedCode, vehicleId)

    @QueryMapping
    fun predictionAccuracy(
        @Argument feedCode: String,
        @Argument algorithm: String?,
        @Argument sinceDays: Int,
    ) = read.predictionAccuracy(feedCode, algorithm?.let { PredictionAlgorithm.valueOf(it) }, sinceDays)

    @QueryMapping
    fun stopPredictions(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument routeId: String?,
        @Argument directionId: Int?,
    ) = read.stopPredictions(feedCode, stopId, routeId, directionId)

    @QueryMapping
    fun headway(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument routeId: String,
        @Argument directionId: Int?,
    ) = headwayRead.headway(feedCode, stopId, routeId, directionId)
}
