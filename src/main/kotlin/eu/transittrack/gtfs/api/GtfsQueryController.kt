package eu.transittrack.gtfs.api

import eu.transittrack.gtfs.read.GtfsReadService
import eu.transittrack.gtfs.read.GtfsRecordsService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL query entry points for a revision's core GTFS entities. Thin delegation
 * to [GtfsReadService]; revision resolution and mapping live there.
 */
@Controller
class GtfsQueryController(
    private val read: GtfsReadService,
    private val recordsService: GtfsRecordsService,
) {

    @QueryMapping
    fun gtfsAgencies(@Argument feedCode: String, @Argument revisionId: String?) =
        read.agencies(feedCode, revisionId)

    @QueryMapping
    fun gtfsRoutes(@Argument feedCode: String, @Argument revisionId: String?) =
        read.routes(feedCode, revisionId)

    @QueryMapping
    fun gtfsRoute(@Argument feedCode: String, @Argument routeId: String, @Argument revisionId: String?) =
        read.route(feedCode, routeId, revisionId)

    @QueryMapping
    fun gtfsStops(@Argument feedCode: String, @Argument revisionId: String?) =
        read.stops(feedCode, revisionId)

    @QueryMapping
    fun gtfsStop(@Argument feedCode: String, @Argument stopId: String, @Argument revisionId: String?) =
        read.stop(feedCode, stopId, revisionId)

    @QueryMapping
    fun gtfsTrips(
        @Argument feedCode: String,
        @Argument routeId: String?,
        @Argument serviceId: String?,
        @Argument revisionId: String?,
    ) = read.trips(feedCode, routeId, serviceId, revisionId)

    @QueryMapping
    fun gtfsTrip(@Argument feedCode: String, @Argument tripId: String, @Argument revisionId: String?) =
        read.trip(feedCode, tripId, revisionId)

    @QueryMapping
    fun gtfsStopTimes(@Argument feedCode: String, @Argument tripId: String, @Argument revisionId: String?) =
        read.stopTimes(feedCode, tripId, revisionId)

    @QueryMapping
    fun gtfsCalendars(@Argument feedCode: String, @Argument revisionId: String?) =
        read.calendars(feedCode, revisionId)

    @QueryMapping
    fun gtfsCalendarDates(
        @Argument feedCode: String,
        @Argument serviceId: String?,
        @Argument revisionId: String?,
    ) = read.calendarDates(feedCode, serviceId, revisionId)

    @QueryMapping
    fun gtfsShape(@Argument feedCode: String, @Argument shapeId: String, @Argument revisionId: String?) =
        read.shape(feedCode, shapeId, revisionId)

    @QueryMapping
    fun gtfsFrequencies(@Argument feedCode: String, @Argument tripId: String?, @Argument revisionId: String?) =
        read.frequencies(feedCode, tripId, revisionId)

    @QueryMapping
    fun gtfsTransfers(@Argument feedCode: String, @Argument revisionId: String?) =
        read.transfers(feedCode, revisionId)

    @QueryMapping
    fun gtfsFeedInfo(@Argument feedCode: String, @Argument revisionId: String?) =
        read.feedInfo(feedCode, revisionId)

    @QueryMapping
    fun gtfsPathways(@Argument feedCode: String, @Argument revisionId: String?) =
        read.pathways(feedCode, revisionId)

    @QueryMapping
    fun gtfsLevels(@Argument feedCode: String, @Argument revisionId: String?) =
        read.levels(feedCode, revisionId)

    @QueryMapping
    fun gtfsRecords(
        @Argument feedCode: String,
        @Argument table: String,
        @Argument revisionId: String?,
    ) = recordsService.records(feedCode, table, revisionId)
}
