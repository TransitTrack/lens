package eu.transittrack.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import eu.transittrack.CacheProperties
import eu.transittrack.avl.match.cache.AvlCaches

/**
 * Programmatic Caffeine setup for the AVL match read cache. Every cache in [AvlCaches.NAMES] is
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
    fun cacheManager(): CacheManager {
        val manager = CaffeineCacheManager(*AvlCaches.NAMES.toTypedArray())
        manager.setCaffeine(
            Caffeine
                .newBuilder()
                .expireAfterWrite(props.ttl)
                .maximumSize(props.maxEntries),
        )
        return manager
    }
}
