package eu.transittrack.predict.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role
import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.read.HeadwayReadService
import eu.transittrack.predict.read.PredictionReadService

/**
 * GraphQL query entry points for the prediction read layer. Thin delegation to
 * [PredictionReadService]. Registers unconditionally (only generation/retention are feature-gated).
 */
@Controller
@ConditionalOnRole(Role.API)
class PredictionQueryController(
    private val read: PredictionReadService,
    private val headwayRead: HeadwayReadService,
) {
    @QueryMapping
    fun vehiclePredictions(
        @Argument feedCode: String,
        @Argument vehicleId: String,
        @Argument algorithm: PredictionAlgorithm?,
    ) = read.vehiclePredictions(feedCode, vehicleId, algorithm)

    @QueryMapping
    fun predictionAccuracy(
        @Argument feedCode: String,
        @Argument algorithm: PredictionAlgorithm?,
        @Argument sinceDays: Int,
    ) = read.predictionAccuracy(feedCode, algorithm, sinceDays)

    @QueryMapping
    fun stopPredictions(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument routeId: String?,
        @Argument directionId: Int?,
        @Argument algorithm: PredictionAlgorithm?,
    ) = read.stopPredictions(feedCode, stopId, routeId, directionId, algorithm)

    @QueryMapping
    fun headway(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument routeId: String,
        @Argument directionId: Int?,
        @Argument algorithm: PredictionAlgorithm?,
    ) = headwayRead.headway(feedCode, stopId, routeId, directionId, algorithm)
}
