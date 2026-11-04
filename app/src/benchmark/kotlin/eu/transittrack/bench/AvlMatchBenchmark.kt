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
 * Processing (matching) stage: claim PENDING `avl_report` rows, run them through
 * [eu.transittrack.avl.match.DescriptorThenInferMatcher] (trust-descriptor, route+time
 * disambiguation, then full spatial/temporal inference - see [PipelineBenchmarkEnvironment]'s
 * descriptor-mix comment), and batch-write `vehicle_match`/`vehicle_state`. One
 * [eu.transittrack.avl.match.AvlMatchProcessor.processBatch] call per invocation.
 *
 * Isolates matching's own cost from ingest: `avl_report` rows are seeded directly rather than
 * through a real decode+poll.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
class AvlMatchBenchmark {
    @Param("400", "4000")
    var vehicleCount: Int = 0

    private val env = PipelineBenchmarkEnvironment()

    @Setup(Level.Trial)
    fun startTrial() = env.start()

    @TearDown(Level.Trial)
    fun stopTrial() = env.stop()

    @Setup(Level.Invocation)
    fun beforeEachMatch() {
        env.resetAvlTables()
        env.seedPendingAvlReports(vehicleCount, Instant.now())
    }

    @Benchmark
    fun match(): Int = env.matchProcessor().processBatch()
}
