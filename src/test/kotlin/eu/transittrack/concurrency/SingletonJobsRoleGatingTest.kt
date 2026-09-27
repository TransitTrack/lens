package eu.transittrack.concurrency

import kotlin.test.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

import eu.transittrack.avl.ingest.AvlRetentionScheduler
import eu.transittrack.avl.match.AvlSilentVehicleSweeper
import eu.transittrack.avl.observability.AvlMetricsGaugeRefresher
import eu.transittrack.gtfs.export.ExportTempFileSweeper
import eu.transittrack.predict.PredictionRetentionScheduler
import eu.transittrack.schedule.optimize.OptimizationRetentionScheduler
import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest(
    classes = [eu.transittrack.Application::class],
    properties = ["spring.profiles.active=role-predictor"],
)
class SingletonJobsRoleGatingTest(
    @Autowired val ctx: ApplicationContext,
) : PostgresPerMethodTest() {
    @Test
    fun `housekeeping jobs are absent under role-predictor`() {
        assert(ctx.getBeansOfType(AvlSilentVehicleSweeper::class.java).isEmpty())
        assert(ctx.getBeansOfType(AvlRetentionScheduler::class.java).isEmpty())
        assert(ctx.getBeansOfType(PredictionRetentionScheduler::class.java).isEmpty())
        assert(ctx.getBeansOfType(OptimizationRetentionScheduler::class.java).isEmpty())
        assert(ctx.getBeansOfType(ExportTempFileSweeper::class.java).isEmpty())
        assert(ctx.getBeansOfType(AvlMetricsGaugeRefresher::class.java).isEmpty())
    }
}
