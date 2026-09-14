package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class CacheConfigurationTest(
    @Autowired val cacheManager: CacheManager,
) : PostgresPerMethodTest() {
    @Test
    fun `a Caffeine-backed cache manager is configured`() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager::class)
    }

    @Test
    fun `every AVL cache name resolves to a cache`() {
        AvlCaches.NAMES.forEach { name ->
            assertThat(cacheManager.getCache(name)).isNotNull()
        }
    }
}
