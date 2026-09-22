package eu.transittrack.bench

import java.time.Instant
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup

/**
 * Prediction stage: claim PENDING `vehicle_match` rows, detect stop-path crossings (learning
 * `travel_time_observation`/`kalman_travel_time_state` and filling in accuracy samples), and
 * generate fresh `vehicle_prediction` rows for the remaining horizon. One
 * [eu.transittrack.predict.PredictionProcessor.processBatch] call per invocation.
 *
 * Isolates prediction's own cost from ingest/matching: `vehicle_match` rows are seeded via
 * [PipelineBenchmarkEnvironment.seedPendingVehicleMatches] (unmeasured `@Setup` time) rather than
 * driving the full ingest -> match -> predict chain inside the measured call.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
class PredictionBenchmark {
    @Param("400", "4000")
    var vehicleCount: Int = 0

    private val env = PipelineBenchmarkEnvironment()

    @Setup(Level.Trial)
    fun startTrial() = env.start()

    @TearDown(Level.Trial)
    fun stopTrial() = env.stop()

    @Setup(Level.Invocation)
    fun beforeEachPredict() {
        env.resetAvlTables()
        env.seedPendingVehicleMatches(vehicleCount, Instant.now())
    }

    @Benchmark
    fun predict(): Int = env.predictionProcessor().processBatch()
}
