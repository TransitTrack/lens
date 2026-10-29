package eu.transittrack

import java.time.Clock
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import com.github.benmanes.caffeine.cache.Caffeine
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class TimeConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

@Configuration
@EnableScheduling
class AsyncConfiguration(
    private val feedsProperties: FeedsProperties,
    @Value("\${spring.threads.virtual.enabled:false}") private val useVirtualThreads: Boolean,
) {
    @Bean
    fun gtfsIngestExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            queueCapacity = feedsProperties.feeds.size * 5
            setThreadNamePrefix("gtfs-ingest-")
            setVirtualThreads(useVirtualThreads)
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
            val body = response.body

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
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
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
 * Programmatic Caffeine setup for the AVL match read cache. Every cache in `AvlCaches.NAMES` is
 * created heap-only with a shared TTL and max-entry cap from [CacheProperties]. Unlike a JSR-107
 * provider (e.g. EhCache), [CaffeineCacheManager] is a plain Spring-scoped bean with no JVM-wide
 * registry behind it, so distinct `@SpringBootTest` contexts in the same JVM never share — or tear
 * down — each other's cache manager.
 */
@Configuration
@EnableCaching
class CacheConfiguration(
    private val props: CacheProperties,
) {
    @Bean
    fun cacheManagerCustomizer(): CacheManagerCustomizer<CaffeineCacheManager> =
        CacheManagerCustomizer { cacheManager ->
            cacheManager.setCaffeine(
                Caffeine
                    .newBuilder()
                    .maximumSize(props.maxEntries)
                    .expireAfterWrite(props.ttl)
                    .recordStats(), // Required for Micrometer to fetch hit/miss metrics
            )
        }
}
