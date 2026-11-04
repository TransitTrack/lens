package eu.transittrack.avl.match.cache

import org.springframework.cache.CacheManager

/**
 * Clears every AVL read-cache. Call from `@BeforeEach` in any test that asserts on cache hit-counts —
 * the `@SpringBootTest` [CacheManager] is shared across test classes, so entries leak between them.
 */
fun clearAvlCaches(cacheManager: CacheManager) {
    AvlCaches.NAMES.forEach { cacheManager.getCache(it)?.clear() }
}
