package eu.transittrack.config

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.jsr107.Eh107Configuration
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import eu.transittrack.CacheProperties
import eu.transittrack.avl.match.cache.AvlCaches
import javax.cache.configuration.Configuration as JCacheConfiguration

/**
 * Programmatic EhCache 3 (JSR-107) setup for the AVL match read cache. Every cache in
 * [AvlCaches.NAMES] is created heap-only with a shared TTL and max-entry cap from [CacheProperties].
 * Boot auto-configures the [org.springframework.cache.jcache.JCacheCacheManager] once a JCache
 * provider is on the classpath and `@EnableCaching` is present.
 */
@Configuration
@EnableCaching
class CacheConfiguration(
    private val props: CacheProperties,
) {
    @Bean
    fun avlCacheManagerCustomizer(): JCacheManagerCustomizer =
        JCacheManagerCustomizer { cm ->
            AvlCaches.NAMES.forEach { name ->
                if (cm.getCache<Any, Any>(name) == null) cm.createCache(name, heapConfig())
            }
        }

    private fun heapConfig(): JCacheConfiguration<Any, Any> =
        Eh107Configuration.fromEhcacheCacheConfiguration(
            CacheConfigurationBuilder
                .newCacheConfigurationBuilder<Any, Any>(
                    Any::class.java,
                    Any::class.java,
                    ResourcePoolsBuilder.heap(props.maxEntries),
                ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(props.ttl))
                .build(),
        )
}
