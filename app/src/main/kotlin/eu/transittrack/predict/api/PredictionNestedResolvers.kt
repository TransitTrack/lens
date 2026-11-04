package eu.transittrack.predict.api

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.predict.read.dto.LateAssessmentDto
import eu.transittrack.predict.read.dto.StopPredictionDto

/** Field resolvers for `StopPrediction`'s and `LateAssessment`'s reference into the GTFS `Stop` entity. */
@Controller
@ConditionalOnRole(Role.API)
class PredictionNestedResolvers(
    private val stops: StopRepository,
) {
    @SchemaMapping(typeName = "StopPrediction")
    fun stop(dto: StopPredictionDto): StopDto? = stop(dto.revisionId, dto.gtfsFeedCode, dto.stopId)

    @SchemaMapping(typeName = "LateAssessment")
    fun stop(dto: LateAssessmentDto): StopDto? = stop(dto.revisionId, dto.gtfsFeedCode, dto.stopId)

    private fun stop(
        revisionId: Long,
        gtfsFeedCode: String,
        stopId: String,
    ): StopDto? = stops.findByStopId(revisionId, stopId)?.let { StopDto.of(it, revisionId, gtfsFeedCode) }
}
