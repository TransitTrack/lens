package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import org.springframework.aop.aspectj.AspectJPrecedenceInformation
import org.springframework.aop.framework.Advised
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class CacheConfigurationTest(
    @Autowired val cacheManager: CacheManager,
    @Autowired val learningKeys: CachedLearningKeyReader,
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

    /** [CachedLearningKeyReader] runs its query through the Exposed bridge; a cache hit must return before the bridge opens a transaction. */
    @Test
    fun `the learning-key cache is consulted before the Exposed bridge`() {
        val advisors = (learningKeys as Advised).advisors.toList()
        val cache = advisors.indexOfFirst { it is BeanFactoryCacheOperationSourceAdvisor }
        val bridge = advisors.indexOfFirst { (it as? AspectJPrecedenceInformation)?.aspectName == "exposedBridgeAspect" }
        assertThat(cache, name = "cache advisor").isGreaterThanOrEqualTo(0)
        assertThat(bridge, name = "bridge advisor").isGreaterThanOrEqualTo(0)
        assertThat(cache, name = "cache advisor position").isLessThan(bridge)
    }
}
