package eu.transittrack.config

import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetchingEnvironment
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.ForwardingSource
import okio.buffer // Imports the correct extension function
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

import eu.transittrack.HttpClientProperties
import eu.transittrack.gtfs.draft.edit.LockNotHeldException
import eu.transittrack.gtfs.draft.edit.StaleDraftException
import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException
import eu.transittrack.gtfs.feed.FeedProtectedException

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
    class MaxBytesResponseInterceptor(
        private val maxBytes: Long,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val body = response.body ?: return response

            // Optional: Fast-fail using Content-Length header if available
            if (body.contentLength() > maxBytes) {
                throw okio.IOException("Response content-length (${body.contentLength()} bytes) exceeds max limit of $maxBytes bytes.")
            }

            // Wrap the original source to count actual bytes read dynamically
            val limitedSource = object : ForwardingSource(body.source()) {
                private var bytesRead = 0L

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read != -1L) {
                        bytesRead += read
                        if (bytesRead > maxBytes) {
                            throw okio.IOException("Response payload exceeded the max limit of $maxBytes bytes.")
                        }
                    }
                    return read
                }
            }

            // Return a new response with the size-bounded body
            return response
                .newBuilder()
                .body(
                    body.contentType()?.let { type ->
                        limitedSource.buffer().asResponseBody(type, body.contentLength())
                    } ?: response.body,
                ).build()
        }
    }

    @Bean
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
            ).addInterceptor(MaxBytesResponseInterceptor(props.maxSizeBytes))
            .build()
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
