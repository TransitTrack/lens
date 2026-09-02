package eu.transittrack.schedule.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.schedule.read.ScheduleReadService

@Controller
class ScheduleQueryController(
    private val read: ScheduleReadService,
) {
    @QueryMapping
    fun tripPatterns(
        @Argument feedCode: String,
        @Argument routeId: String?,
        @Argument revisionId: String?,
    ) = read.tripPatterns(feedCode, routeId, revisionId)

    @QueryMapping
    fun tripPattern(
        @Argument feedCode: String,
        @Argument patternKey: String,
        @Argument revisionId: String?,
    ) = read.tripPattern(feedCode, patternKey, revisionId)

    @QueryMapping
    fun schedTrip(
        @Argument feedCode: String,
        @Argument tripId: String,
        @Argument revisionId: String?,
    ) = read.schedTrip(feedCode, tripId, revisionId)

    @QueryMapping
    fun blocks(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.blocks(feedCode, revisionId)

    @QueryMapping
    fun block(
        @Argument feedCode: String,
        @Argument blockId: String,
        @Argument serviceId: String,
        @Argument revisionId: String?,
    ) = read.block(feedCode, blockId, serviceId, revisionId)

    @QueryMapping
    fun blocksOnDate(
        @Argument feedCode: String,
        @Argument date: String,
        @Argument revisionId: String?,
    ) = read.blocksOnDate(feedCode, date, revisionId)

    @QueryMapping
    fun tripsOnDate(
        @Argument feedCode: String,
        @Argument date: String,
        @Argument routeId: String?,
        @Argument revisionId: String?,
    ) = read.tripsOnDate(feedCode, date, routeId, revisionId)
}
