package eu.transittrack.predict.api

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.predict.read.dto.StopPredictionDto

/** Field resolvers for `StopPrediction`'s reference into the GTFS `Stop` entity. */
@Controller
class PredictionNestedResolvers(
    private val stops: StopRepository,
) {
    @SchemaMapping(typeName = "StopPrediction")
    fun stop(dto: StopPredictionDto): StopDto? =
        stops.findByStopId(dto.revisionId, dto.stopId)?.let { StopDto.of(it, dto.revisionId, dto.gtfsFeedCode) }
}
