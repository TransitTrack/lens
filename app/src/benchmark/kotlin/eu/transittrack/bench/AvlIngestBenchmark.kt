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

import eu.transittrack.avl.feed.RawAvlPayload

/**
 * Ingest stage: decode a GTFS-RT `VehiclePosition` payload, drop already-seen `(vehicleId, ts)`
 * duplicates and stale (`avl.ingest.maxReportAgeHours`) reports, and batch-insert the rest as
 * PENDING `avl_report` rows - one [eu.transittrack.avl.ingest.AvlIngestService.pollOnce] call per
 * invocation.
 *
 * `vehicleCount` covers a realistic single-feed poll (400, close to what poznan/wroclaw look like
 * in production per this session's DB stats) and a stress case (4000) well past
 * `avl.match.claimBatchSize`/`predict.run.claimBatchSize` (500 each).
 *
 * Run with `./gradlew benchmark` (or `./gradlew benchmark -PbenchmarksInclude=AvlIngestBenchmark`
 * to run just this class - see `docs/benchmarks.md`).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 5)
class AvlIngestBenchmark {
    @Param("400", "4000")
    var vehicleCount: Int = 0

    private val env = PipelineBenchmarkEnvironment()

    @Setup(Level.Trial)
    fun startTrial() = env.start()

    @TearDown(Level.Trial)
    fun stopTrial() = env.stop()

    /** Fresh payload + truncated tables each invocation, so the measured call always does real
     * decode + insert work instead of hitting the dedup fast path on a second identical poll. */
    @Setup(Level.Invocation)
    fun beforeEachPoll() {
        env.resetAvlTables()
        val now = Instant.now()
        env.avlFeedSource.payload = RawAvlPayload(env.gtfsRtPayload(vehicleCount, now.epochSecond), null, now)
    }

    @Benchmark
    fun ingest(): Int = env.ingestService().pollOnce(env.feed)
}
