package eu.transittrack.gtfs.api

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetchingEnvironment
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.RuntimeWiringConfigurer

import eu.transittrack.gtfs.draft.edit.LockNotHeldException
import eu.transittrack.gtfs.draft.edit.StaleDraftException
import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException
import eu.transittrack.gtfs.feed.FeedProtectedException
import eu.transittrack.schedule.optimize.RecommendationConflictException

/**
 * GraphQL wiring for the GTFS surface:
 * - registers the `JSON` scalar (from `graphql-java-extended-scalars`) used by
 *   `GtfsRevision.rowCounts`;
 * - maps GTFS domain / guard exceptions to client-facing GraphQL errors so mutation failures
 *   surface a message instead of a generic `INTERNAL_ERROR`.
 *
 * Imported explicitly by the GraphQL slice tests.
 */
@Configuration
@ConditionalOnClass
class GraphQlConfiguration {
    @Bean
    fun jsonScalar(): RuntimeWiringConfigurer =
        RuntimeWiringConfigurer {
            it.scalar(ExtendedScalars.Json)
            it.scalar(ExtendedScalars.GraphQLLong)
        }

    @Bean
    fun exceptionResolver(): DataFetcherExceptionResolver = ExceptionResolver()

    private class ExceptionResolver : DataFetcherExceptionResolverAdapter() {
        override fun resolveToSingleError(
            ex: Throwable,
            env: DataFetchingEnvironment,
        ): GraphQLError? {
            if (ex is StaleDraftException) {
                return GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.message ?: "draft was modified concurrently")
                    .extensions(mapOf("code" to "STALE_DRAFT", "currentVersion" to ex.currentVersion))
                    .build()
            }
            if (ex is RecommendationConflictException) {
                return GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.message ?: "conflicting optimization recommendations selected")
                    .extensions(
                        mapOf(
                            "code" to "RECOMMENDATION_CONFLICT",
                            "conflictingRecommendationIds" to ex.conflictingRecommendationIds.toList(),
                        ),
                    ).build()
            }
            if (ex is LockNotHeldException) {
                return GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.message ?: "editor lock not held")
                    .extensions(mapOf("code" to "LOCK_LOST"))
                    .build()
            }
            val type =
                when (ex) {
                    is FeedNotFoundException -> ErrorType.NOT_FOUND

                    is FeedConflictException,
                    is FeedProtectedException,
                    -> ErrorType.BAD_REQUEST

                    is IllegalArgumentException,
                    is IllegalStateException,
                    -> ErrorType.BAD_REQUEST

                    else -> return null
                }
            return GraphqlErrorBuilder
                .newError(env)
                .errorType(type)
                .message(ex.message ?: ex.javaClass.simpleName)
                .build()
        }
    }
}
