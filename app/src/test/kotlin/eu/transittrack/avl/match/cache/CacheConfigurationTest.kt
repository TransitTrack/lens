package eu.transittrack.avl.match.cache

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import org.springframework.aop.framework.Advised
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor

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

    /** [CachedLearningKeyReader] is `@Transactional`; a cache hit must return before a transaction is begun. */
    @Test
    fun `the learning-key cache is consulted before a transaction begins`() {
        val advisors = (learningKeys as Advised).advisors.toList()
        val cache = advisors.indexOfFirst { it is BeanFactoryCacheOperationSourceAdvisor }
        val transaction = advisors.indexOfFirst { it is BeanFactoryTransactionAttributeSourceAdvisor }
        assertThat(cache, name = "cache advisor").isGreaterThanOrEqualTo(0)
        assertThat(transaction, name = "transaction advisor").isGreaterThanOrEqualTo(0)
        assertThat(cache, name = "cache advisor position").isLessThan(transaction)
    }
}
