package eu.transittrack.reports.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.reports.read.AdherenceReadService

/**
 * GraphQL query entry points for the Schedule Adherence reports read layer. Thin delegation to
 * [AdherenceReadService] - mirrors [eu.transittrack.avl.api.AvlQueryController].
 */
@Controller
@ConditionalOnRole(Role.API)
class AdherenceQueryController(
    private val read: AdherenceReadService,
) {
    @QueryMapping
    fun routeAdherence(
        @Argument feedCode: String,
        @Argument routeId: String,
        @Argument startDate: String,
        @Argument endDate: String,
    ) = read.routeAdherence(feedCode, routeId, startDate, endDate)

    @QueryMapping
    fun allRoutesAdherence(
        @Argument feedCode: String,
        @Argument startDate: String,
        @Argument endDate: String,
    ) = read.allRoutesAdherence(feedCode, startDate, endDate)

    @QueryMapping
    fun tripAdherence(
        @Argument feedCode: String,
        @Argument tripId: String,
        @Argument startDate: String,
        @Argument endDate: String,
    ) = read.tripAdherence(feedCode, tripId, startDate, endDate)

    @QueryMapping
    fun allTripsAdherence(
        @Argument feedCode: String,
        @Argument routeId: String?,
        @Argument startDate: String,
        @Argument endDate: String,
    ) = read.allTripsAdherence(feedCode, routeId, startDate, endDate)

    @QueryMapping
    fun stopAdherence(
        @Argument feedCode: String,
        @Argument stopId: String,
        @Argument startDate: String,
        @Argument endDate: String,
    ) = read.stopAdherence(feedCode, stopId, startDate, endDate)
}
