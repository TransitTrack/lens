package eu.transittrack.gtfs.config

import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException
import eu.transittrack.gtfs.feed.FeedProtectedException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetchingEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * GraphQL wiring for the GTFS surface:
 *  - registers the `JSON` scalar (from `graphql-java-extended-scalars`) used by
 *    `GtfsRevision.rowCounts`;
 *  - maps GTFS domain / guard exceptions to client-facing GraphQL errors so mutation
 *    failures surface a message instead of a generic `INTERNAL_ERROR`.
 *
 * Imported explicitly by the GraphQL slice tests.
 */
@Configuration
class GtfsGraphQlConfig {

    @Bean
    fun gtfsJsonScalar(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer { it.scalar(ExtendedScalars.Json) }

    @Bean
    fun gtfsExceptionResolver(): DataFetcherExceptionResolver = GtfsExceptionResolver()

    private class GtfsExceptionResolver : DataFetcherExceptionResolverAdapter() {
        override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
            val type = when (ex) {
                is FeedNotFoundException -> ErrorType.NOT_FOUND
                is FeedConflictException, is FeedProtectedException -> ErrorType.BAD_REQUEST
                is IllegalArgumentException, is IllegalStateException -> ErrorType.BAD_REQUEST
                else -> return null
            }
            return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.message ?: ex.javaClass.simpleName)
                .build()
        }
    }
}
