package eu.transittrack.config

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetchingEnvironment
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException
import eu.transittrack.gtfs.feed.FeedProtectedException
import eu.transittrack.http.HttpClientProperties

@Configuration
@EnableScheduling
class AsyncConfiguration {
    @Bean
    fun gtfsIngestExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 50
            setThreadNamePrefix("gtfs-ingest-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
}

@Configuration
class HttpClientsConfiguration(
    val props: HttpClientProperties,
) {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    fun okHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(props.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(props.readTimeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .addInterceptor {
                val modifiedRequest =
                    it
                        .request()
                        .newBuilder()
                        .header("User-Agent", props.userAgent)
                        .build()
                it.proceed(modifiedRequest)
            }.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            ).build()
}

@Configuration
@EntityScan(basePackages = ["eu.transittrack"])
@EnableJpaRepositories(basePackages = ["eu.transittrack"])
class DatabaseConfiguration

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
        }

    @Bean
    fun exceptionResolver(): DataFetcherExceptionResolver = ExceptionResolver()

    private class ExceptionResolver : DataFetcherExceptionResolverAdapter() {
        override fun resolveToSingleError(
            ex: Throwable,
            env: DataFetchingEnvironment,
        ): GraphQLError? {
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
