package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.read.GtfsReadService
import eu.transittrack.gtfs.read.GtfsRecordsService

/**
 * GraphQL query entry points for a revision's core GTFS entities. Thin delegation to
 * [GtfsReadService]; revision resolution and mapping live there.
 */
@Controller
class GtfsQueryController(
    private val read: GtfsReadService,
    private val recordsService: GtfsRecordsService,
) {
    @QueryMapping
    fun agencies(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.agencies(feedCode, revisionId)

    @QueryMapping
    fun routes(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.routes(feedCode, revisionId)

    @QueryMapping
    fun route(
        @Argument feedCode: String,
        @Argument routeId: String,
        @Argument revisionId: String?,
    ) = read.route(feedCode, routeId, revisionId)

    @QueryMapping
    fun stops(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.stops(feedCode, revisionId)

    @QueryMapping
    fun stop(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument revisionId: String?,
    ) = read.stop(feedCode, stopId, revisionId)

    @QueryMapping
    fun trips(
        @Argument feedCode: String,
        @Argument routeId: String?,
        @Argument serviceId: String?,
        @Argument revisionId: String?,
    ) = read.trips(feedCode, routeId, serviceId, revisionId)

    @QueryMapping
    fun trip(
        @Argument feedCode: String,
        @Argument tripId: String,
        @Argument revisionId: String?,
    ) = read.trip(feedCode, tripId, revisionId)

    @QueryMapping
    fun stopTimes(
        @Argument feedCode: String,
        @Argument tripId: String,
        @Argument revisionId: String?,
    ) = read.stopTimes(feedCode, tripId, revisionId)

    @QueryMapping
    fun calendars(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.calendars(feedCode, revisionId)

    @QueryMapping
    fun calendarDates(
        @Argument feedCode: String,
        @Argument serviceId: String?,
        @Argument revisionId: String?,
    ) = read.calendarDates(feedCode, serviceId, revisionId)

    @QueryMapping
    fun shape(
        @Argument feedCode: String,
        @Argument shapeId: String,
        @Argument revisionId: String?,
    ) = read.shape(feedCode, shapeId, revisionId)

    @QueryMapping
    fun frequencies(
        @Argument feedCode: String,
        @Argument tripId: String?,
        @Argument revisionId: String?,
    ) = read.frequencies(feedCode, tripId, revisionId)

    @QueryMapping
    fun transfers(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.transfers(feedCode, revisionId)

    @QueryMapping
    fun feedInfo(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.feedInfo(feedCode, revisionId)

    @QueryMapping
    fun pathways(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.pathways(feedCode, revisionId)

    @QueryMapping
    fun levels(
        @Argument feedCode: String,
        @Argument revisionId: String?,
    ) = read.levels(feedCode, revisionId)

    @QueryMapping
    fun records(
        @Argument feedCode: String,
        @Argument table: String,
        @Argument revisionId: String?,
    ) = recordsService.records(feedCode, table, revisionId)
}
