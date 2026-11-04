package eu.transittrack.schedule.optimize

import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRepository
import eu.transittrack.schedule.optimize.model.OptimizationRunRow
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
class OptimizationLockTest(
    @Autowired val runs: OptimizationRunRepository,
    @Autowired val recommendations: OptimizationRecommendationRepository,
    @Autowired val txManager: PlatformTransactionManager,
) : PostgresPerMethodTest() {
    private fun newRecommendations(): Pair<Long, Long> {
        val run =
            runs.save(
                OptimizationRunRow(
                    feedCode = "lock",
                    revisionId = 1,
                    observedFrom = Instant.parse("2026-09-01T00:00:00Z"),
                    observedTo = Instant.parse("2026-09-08T00:00:00Z"),
                    minimumSamples = 1,
                ),
            )

        fun rec() =
            recommendations
                .save(
                    OptimizationRecommendationRow(
                        runId = run.id!!,
                        kind = OptimizationRecommendationKind.STOP_TIME,
                        sampleCount = 1,
                        deltaSec = 1,
                        reason = "r",
                    ),
                ).id!!
        return rec() to rec()
    }

    @Test
    fun lockForUpdateBlocksSecondTransaction() {
        val (shared, _) = newRecommendations()
        val pool = Executors.newFixedThreadPool(2)
        val aHoldsLock = CountDownLatch(1)
        try {
            val a =
                pool.submit {
                    TransactionTemplate(txManager).executeWithoutResult {
                        recommendations.findAllByIdForUpdate(listOf(shared))
                        aHoldsLock.countDown()
                        Thread.sleep(500)
                    }
                }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "thread A never took the lock" }
            val b =
                pool.submit<Long> {
                    measureTimeMillis {
                        TransactionTemplate(txManager).executeWithoutResult { recommendations.findAllByIdForUpdate(listOf(shared)) }
                    }
                }
            a.get(10, TimeUnit.SECONDS)
            assertThat(b.get(10, TimeUnit.SECONDS)).isGreaterThanOrEqualTo(450L)
        } finally {
            pool.shutdownNow()
        }
    }

    /** Disjoint selections lock disjoint rows and proceed concurrently. */
    @Test
    fun lockOnOtherRowDoesNotBlock() {
        val (first, second) = newRecommendations()
        val pool = Executors.newFixedThreadPool(2)
        val aHoldsLock = CountDownLatch(1)
        try {
            val a =
                pool.submit {
                    TransactionTemplate(txManager).executeWithoutResult {
                        recommendations.findAllByIdForUpdate(listOf(first))
                        aHoldsLock.countDown()
                        Thread.sleep(1500)
                    }
                }
            check(aHoldsLock.await(10, TimeUnit.SECONDS)) { "thread A never took the lock" }
            val b =
                pool.submit<Long> {
                    measureTimeMillis {
                        TransactionTemplate(txManager).executeWithoutResult { recommendations.findAllByIdForUpdate(listOf(second)) }
                    }
                }
            assertThat(b.get(10, TimeUnit.SECONDS)).isLessThan(1000L)
            a.get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }
}
