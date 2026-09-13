package eu.transittrack.config

import java.net.URLClassLoader
import javax.cache.Caching

import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.jsr107.Eh107Configuration
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
    /**
     * Boot's JCache autoconfiguration falls back to `cachingProvider.getCacheManager(null, ...)`
     * when no `spring.cache.jcache.config` is set, which per the JSR-107 spec resolves to one
     * process-wide default `javax.cache.CacheManager` shared by every JVM-local caller — including
     * every distinct `@SpringBootTest` context in a test run. Closing any one such context (Spring's
     * test context cache evicting it, or the JVM shutting down) then closes that shared manager out
     * from under every other still-live context ("Cache[...] is closed"). Ehcache's provider keys its
     * manager cache by `(classLoader, uri)`; a non-default URI is treated as an XML config location
     * (`uri.toURL()`), so the only way to keep the default (programmatic, non-XML) configuration while
     * still getting a manager private to this Spring context is to key on a throwaway classloader
     * instead — one per `CacheConfiguration` instance, GC'd (it's a `WeakHashMap` key) once the
     * context closes.
     */
    @Bean
    fun jCacheCacheManager(): javax.cache.CacheManager {
        val cm =
            Caching
                .getCachingProvider()
                .getCacheManager(null, URLClassLoader(emptyArray(), javaClass.classLoader))
        // Boot's own jCacheCacheManager @Bean method (bypassed once we supply this bean ourselves)
        // is what normally applies JCacheManagerCustomizer beans, so this has to do it directly.
        AvlCaches.NAMES.forEach { name ->
            if (cm.getCache<Any, Any>(name) == null) cm.createCache(name, heapConfig())
        }
        return cm
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
