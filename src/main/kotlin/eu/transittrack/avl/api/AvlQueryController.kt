package eu.transittrack.avl.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.avl.read.AvlReadService

/**
 * GraphQL query entry points for the AVL read layer. Thin delegation to [AvlReadService].
 * Registers unconditionally (only the poller / processor / retention are feature-gated).
 */
@Controller
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
}
