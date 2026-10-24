package eu.transittrack.avl.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.avl.read.AvlReadService
import eu.transittrack.config.ConditionalOnRole
import eu.transittrack.config.Role

/**
 * GraphQL query entry points for the AVL read layer. Thin delegation to [AvlReadService].
 * Registers unconditionally (only the poller / processor / retention are feature-gated).
 */
@Controller
@ConditionalOnRole(Role.API)
class AvlQueryController(
    private val read: AvlReadService,
) {
    @QueryMapping
    fun avlFeeds() = read.avlFeeds()

    @QueryMapping
    fun vehicles(
        @Argument feedCode: String,
        @Argument matchedOnly: Boolean,
    ) = read.vehicles(feedCode, matchedOnly)

    @QueryMapping
    fun vehicle(
        @Argument feedCode: String,
        @Argument vehicleId: String,
    ) = read.vehicle(feedCode, vehicleId)

    @QueryMapping
    fun avlReports(
        @Argument feedCode: String,
        @Argument vehicleId: String,
        @Argument since: String?,
        @Argument limit: Int,
    ) = read.avlReports(feedCode, vehicleId, since, limit)

    @QueryMapping
    fun avlReportCatalog(
        @Argument feedCode: String,
    ) = read.avlReportCatalog(feedCode)

    @QueryMapping
    fun avlReportPage(
        @Argument feedCode: String,
        @Argument vehicleId: String,
        @Argument date: String,
        @Argument startTime: String,
        @Argument endTime: String,
        @Argument page: Int,
        @Argument pageSize: Int,
    ) = read.avlReportPage(feedCode, vehicleId, date, startTime, endTime, page, pageSize)
}
