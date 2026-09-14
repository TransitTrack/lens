package eu.transittrack.schedule.compare.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.schedule.compare.CalendarChange
import eu.transittrack.schedule.compare.HeadwaySummary
import eu.transittrack.schedule.compare.ScheduleComparisonService
import eu.transittrack.schedule.compare.TripChange

@Controller
class ScheduleComparisonController(
    private val service: ScheduleComparisonService,
) {
    @QueryMapping
    fun compareRevisions(
        @Argument fromRevisionId: String,
        @Argument toRevisionId: String,
        @Argument routeId: String?,
        @Argument directionId: Int?,
        @Argument serviceId: String?,
        @Argument offset: Int,
        @Argument limit: Int,
    ): ScheduleComparisonDto {
        val cmp = service.compare(fromRevisionId.toLong(), toRevisionId.toLong(), routeId, directionId, serviceId)
        val paged = cmp.tripChanges.drop(offset).take(limit)
        return ScheduleComparisonDto(
            fromRevisionId = cmp.fromRevisionId.toString(),
            toRevisionId = cmp.toRevisionId.toString(),
            fromDerivationStale = cmp.fromDerivationStale,
            toDerivationStale = cmp.toDerivationStale,
            tripChangeCount = cmp.tripChanges.size,
            tripChanges = paged.map(::toDto),
            calendarChanges = cmp.calendarChanges.map(::toDto),
            headwaySummaries = cmp.headwaySummaries.map(::toDto),
        )
    }

    private fun toDto(c: TripChange) =
        TripChangeDto(
            tripId = c.tripId,
            kind = c.kind.name,
            fieldChanges = c.fieldChanges.mapValues { (_, v) -> mapOf("from" to v.first, "to" to v.second) },
            stopChanges =
                c.stopChanges.map { s ->
                    StopTimeChangeDto(s.stopSequence, s.kind.name, s.stopId, s.arrivalDeltaSec, s.departureDeltaSec)
                },
            runTimeDeltaSec = c.runTimeDeltaSec,
        )

    private fun toDto(c: CalendarChange) =
        CalendarChangeDto(
            serviceId = c.serviceId,
            kind = c.kind.name,
            fieldChanges = c.fieldChanges.mapValues { (_, v) -> mapOf("from" to v.first, "to" to v.second) },
            exceptionChanges =
                c.exceptionChanges.map { e ->
                    CalendarExceptionChangeDto(e.date.toString(), e.kind.name, e.fromType, e.toType)
                },
        )

    private fun toDto(h: HeadwaySummary) =
        HeadwaySummaryDto(
            routeId = h.routeId,
            directionId = h.directionId,
            serviceId = h.serviceId,
            fromTripCount = h.fromTripCount,
            toTripCount = h.toTripCount,
            fromMeanGapSec = h.fromMeanGapSec,
            toMeanGapSec = h.toMeanGapSec,
            fromMaxGapSec = h.fromMaxGapSec,
            toMaxGapSec = h.toMaxGapSec,
        )
}
