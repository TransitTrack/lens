package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.jcache.JCacheCacheManager
import org.springframework.context.annotation.Import

import eu.transittrack.TestcontainersConfiguration

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class)
class CacheConfigurationTest(
    @Autowired val cacheManager: CacheManager,
) {
    @Test
    fun `a JCache-backed cache manager is configured`() {
        assertThat(cacheManager).isInstanceOf(JCacheCacheManager::class)
    }

    @Test
    fun `every AVL cache name resolves to a cache`() {
        AvlCaches.NAMES.forEach { name ->
            assertThat(cacheManager.getCache(name)).isNotNull()
        }
    }
}
