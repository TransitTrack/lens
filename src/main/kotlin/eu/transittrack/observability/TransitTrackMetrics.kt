package eu.transittrack.observability

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The sole owner of TransitTrack business metrics. Keep labels bounded: feeds and configuration
 * enums are useful dimensions; vehicle/trip/revision identifiers and exception messages are not.
 */
@Component
class TransitTrackMetrics(
    private val registry: MeterRegistry,
) {
    companion object {
        /** Lightweight opt-in fallback for direct service construction in unit tests. */
        fun forTests(): TransitTrackMetrics = TransitTrackMetrics(SimpleMeterRegistry())
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val gauges = ConcurrentHashMap<String, AtomicLong>()
    private val ageTimestamps = ConcurrentHashMap<String, AtomicLong>()

    enum class Outcome { SUCCESS, FAILED }

    enum class ImportOutcome { READY, UNCHANGED, FAILED }

    enum class ImportStage { DOWNLOAD, VALIDATE, PARSE, DERIVE, ACTIVATE }

    enum class MatchMetricOutcome { MATCHED, FAILED, SKIPPED }

    /** REJECTED = the caller's input/state was invalid (bad ids, wrong run state, stale target) —
     * distinct from FAILURE (an unexpected exception during the apply pipeline itself), so an
     * operator can tell "clients are sending bad requests" from "the apply pipeline is broken". */
    enum class ApplyOutcome { SUCCESS, CONFLICT, REJECTED, FAILURE }

    fun gtfsImportStarted(feed: String) = setGauge("transittrack.gtfs.import.in.progress", tags("feed", feed), 1)

    fun gtfsImportFinished(
        feed: String,
        outcome: ImportOutcome,
        elapsed: Duration,
    ) = safely("gtfs_import_finished") {
        val tags = tags("feed", feed, "outcome", outcome.tag())
        counter("transittrack.gtfs.imports", tags).increment()
        timer("transittrack.gtfs.import.duration", tags).record(elapsed)
        setGauge("transittrack.gtfs.import.in.progress", tags("feed", feed), 0)
        if (outcome !=
            ImportOutcome.FAILED
        ) {
            setGauge("transittrack.gtfs.feed.last.success.timestamp", tags("feed", feed), Instant.now().epochSecond)
        }
    }

    fun gtfsStageFinished(
        feed: String,
        stage: ImportStage,
        outcome: Outcome,
        elapsed: Duration,
    ) = safely("gtfs_stage_finished") {
        timer("transittrack.gtfs.import.stage.duration", tags("feed", feed, "stage", stage.tag(), "outcome", outcome.tag())).record(elapsed)
    }

    fun gtfsDownloadBytes(
        feed: String,
        bytes: Long,
    ) = safely("gtfs_download_bytes") {
        counter("transittrack.gtfs.import.download.bytes", tags("feed", feed)).increment(bytes.toDouble())
    }

    fun gtfsRowsWritten(
        feed: String,
        entityType: String,
        rows: Long,
    ) = safely("gtfs_rows_written") {
        counter("transittrack.gtfs.import.rows", tags("feed", feed, "entity_type", entityType)).increment(rows.toDouble())
    }

    fun avlPollFinished(
        feedCode: String,
        format: String,
        outcome: Outcome,
        elapsed: Duration,
    ) = safely("avl_poll_finished") {
        val tags = avlTags(feedCode, format, "outcome", outcome.tag())
        counter("transittrack.avl.polls", tags).increment()
        timer("transittrack.avl.poll.duration", tags).record(elapsed)
        if (outcome ==
            Outcome.SUCCESS
        ) {
            val now = Instant.now()
            setGauge("transittrack.avl.feed.last.success.timestamp", tags("feed", feedCode), now.epochSecond)
            setAgeGauge("transittrack.avl.feed.age", tags("feed", feedCode), now)
        }
    }

    fun avlReports(
        feedCode: String,
        format: String,
        decoded: Int,
        accepted: Int,
        stale: Int = 0,
    ) = safely("avl_reports") {
        val tags = avlTags(feedCode, format)
        counter("transittrack.avl.reports", tags + Tag.of("result", "decoded")).increment(decoded.toDouble())
        counter("transittrack.avl.reports", tags + Tag.of("result", "accepted")).increment(accepted.toDouble())
        counter("transittrack.avl.reports", tags + Tag.of("result", "stale")).increment(stale.toDouble())
        counter("transittrack.avl.reports", tags + Tag.of("result", "duplicate"))
            .increment((decoded - accepted - stale).coerceAtLeast(0).toDouble())
    }

    /**
     * Count of GTFS services active today for [feedCode]'s active revision — zero means every AVL
     * report that can't resolve its trip by a literal descriptor match (the common case whenever
     * the feed's real-time `trip_id`s don't line up 1:1 with the static schedule) is guaranteed to
     * go UNMATCHED, since both `TrustDescriptorMatcher`'s route+time fallback and
     * `FullInferenceMatcher` require a non-empty `activeServiceIds`. Surfaces schedule/calendar
     * timing problems (e.g. a newly-activated revision whose `calendar.txt` doesn't cover the
     * current date yet) as a metric instead of only as a drop in match rate.
     */
    fun avlActiveServices(
        feedCode: String,
        activeServiceCount: Int,
    ) = safely("avl_active_services") {
        setGauge("transittrack.avl.feed.active.services", tags("feed", feedCode), activeServiceCount.toLong())
        if (activeServiceCount == 0) {
            log.warn(
                "avl feed '{}': zero active GTFS services for today - matching will fail for reports without a literal trip_id match",
                feedCode,
            )
        }
    }

    fun avlQueue(
        feed: String,
        pending: Long,
        oldestCreatedAt: Instant?,
    ) {
        setGauge("transittrack.avl.pending.reports", tags("feed", feed), pending)
        val ageSeconds = oldestCreatedAt?.let { Duration.between(it, Instant.now()).seconds.coerceAtLeast(0) } ?: 0
        setGauge("transittrack.avl.oldest.pending.report.age", tags("feed", feed), ageSeconds)
    }

    fun avlMatchBatch(
        feedCode: String,
        outcome: Outcome,
        elapsed: Duration,
        count: Int,
    ) = safely("avl_match_batch") {
        val tags = tags("feed", feedCode, "outcome", outcome.tag())
        counter("transittrack.avl.match.batches", tags).increment()
        timer("transittrack.avl.match.batch.duration", tags).record(elapsed)
        DistributionSummary
            .builder("transittrack.avl.match.batch.size")
            .tags(tags)
            .register(registry)
            .record(count.toDouble())
    }

    fun predictionBatch(
        feedCode: String,
        outcome: Outcome,
        elapsed: Duration,
        count: Int,
    ) = safely("prediction_batch") {
        val tags = tags("feed", feedCode, "outcome", outcome.tag())
        counter("transittrack.prediction.batches", tags).increment()
        timer("transittrack.prediction.batch.duration", tags).record(elapsed)
        DistributionSummary
            .builder("transittrack.prediction.batch.size")
            .tags(tags)
            .register(registry)
            .record(count.toDouble())
    }

    fun predictionQueue(
        feedCode: String,
        pending: Long,
        oldestCreatedAt: Instant?,
    ) {
        setGauge("transittrack.prediction.pending.matches", tags("feed", feedCode), pending)
        val ageSeconds = oldestCreatedAt?.let { Duration.between(it, Instant.now()).seconds.coerceAtLeast(0) } ?: 0
        setGauge("transittrack.prediction.oldest.pending.match.age", tags("feed", feedCode), ageSeconds)
    }

    /** [deviationM]/[score] are only present when the caller's match outcome was actually matched. */
    fun avlReportMatched(
        feedCode: String,
        assignmentMode: String,
        outcome: MatchMetricOutcome,
        elapsed: Duration,
        deviationM: Double? = null,
        score: Double? = null,
    ) = safely("avl_report_matched") {
        val tags = tags("feed", feedCode, "assignment_mode", assignmentMode, "outcome", outcome.tag())
        counter("transittrack.avl.reports.matched", tags).increment()
        timer("transittrack.avl.match.duration", tags).record(elapsed)
        if (deviationM != null) {
            DistributionSummary
                .builder("transittrack.avl.match.deviation")
                .tags(tags("feed", feedCode, "assignment_mode", assignmentMode))
                .baseUnit("meters")
                .register(registry)
                .record(deviationM)
            score?.let {
                DistributionSummary
                    .builder("transittrack.avl.match.score")
                    .tags(tags("feed", feedCode, "assignment_mode", assignmentMode))
                    .register(registry)
                    .record(it)
            }
        }
    }

    fun predictionRun(
        feedCode: String,
        algorithm: String,
        outcome: Outcome,
        elapsed: Duration,
        generated: Int,
    ) = safely("prediction_run") {
        val tags = tags("feed", feedCode, "algorithm", algorithm, "outcome", outcome.tag())
        counter("transittrack.prediction.runs", tags).increment()
        timer("transittrack.prediction.run.duration", tags).record(elapsed)
        if (outcome ==
            Outcome.SUCCESS
        ) {
            counter("transittrack.predictions.generated", tags("feed", feedCode, "algorithm", algorithm))
                .increment(generated.toDouble())
        }
        setGauge("transittrack.prediction.last.computed.timestamp", tags("feed", feedCode), Instant.now().epochSecond)
    }

    fun predictionCrossings(
        feedCode: String,
        count: Int,
    ) = increment("transittrack.prediction.crossings", tags("feed", feedCode), count)

    fun predictionLearningSamples(
        feedCode: String,
        count: Int,
    ) = increment("transittrack.prediction.learning.samples", tags("feed", feedCode), count)

    fun predictionAccuracy(
        feedCode: String,
        algorithm: String,
        errorSec: Int,
    ) = safely("prediction_accuracy") {
        val tags = tags("feed", feedCode, "algorithm", algorithm)
        counter("transittrack.prediction.accuracy.samples", tags).increment()
        DistributionSummary
            .builder("transittrack.prediction.error")
            .tags(tags)
            .baseUnit("seconds")
            .register(registry)
            .record(errorSec.toDouble())
        DistributionSummary
            .builder("transittrack.prediction.absolute.error")
            .tags(tags)
            .baseUnit("seconds")
            .register(registry)
            .record(kotlin.math.abs(errorSec).toDouble())
    }

    /**
     * A `ScheduleOptimizationService` run reached a terminal state (`SUCCEEDED`/`FAILED`). Only the
     * bounded state name is tagged — never the run id, feed, or planner identity.
     */
    fun optimizationRunFinished(
        state: String,
        elapsed: Duration,
    ) = safely("optimization_run_finished") {
        val tags = tags("state", state.lowercase())
        counter("transittrack.schedule.optimization.runs", tags).increment()
        timer("transittrack.schedule.optimization.run.duration", tags).record(elapsed)
    }

    /** One `OptimizationRecommendationRow` persisted by a run's analysis. */
    fun optimizationRecommendation(
        kind: String,
        status: String,
    ) = safely("optimization_recommendation") {
        counter("transittrack.schedule.optimization.recommendations", tags("kind", kind.lowercase(), "status", status.lowercase()))
            .increment()
    }

    /** Outcome of one [eu.transittrack.schedule.optimize.ScheduleOptimizationService.apply] call. */
    fun optimizationApply(outcome: ApplyOutcome) =
        safely("optimization_apply") {
            counter("transittrack.schedule.optimization.apply", tags("outcome", outcome.tag())).increment()
        }

    /** An [eu.transittrack.schedule.optimize.OptimizationAnalyzer] threw during a run; the run still
     * completes with whatever the other analyzers produced. `analyzerName` is a small fixed set of
     * our own component names (e.g. `"stop-time"`), never user input. */
    fun optimizationAnalyzerFailure(analyzerName: String) =
        safely("optimization_analyzer_failure") {
            counter("transittrack.schedule.optimization.analyzer.failures", tags("analyzer", analyzerName)).increment()
        }

    /**
     * One run of a `@Scheduled` retention/sweep job finished (success or failure). `job` is a small
     * fixed set of our own job names (e.g. `"avl_retention_prune"`), never user input. `items` is an
     * optional count of rows/files affected, for jobs where that's a single meaningful number.
     */
    fun scheduledJobFinished(
        job: String,
        outcome: Outcome,
        elapsed: Duration,
        items: Long? = null,
    ) = safely("scheduled_job_finished") {
        val tags = tags("job", job, "outcome", outcome.tag())
        counter("transittrack.scheduled.job.runs", tags).increment()
        timer("transittrack.scheduled.job.duration", tags).record(elapsed)
        setGauge("transittrack.scheduled.job.last.run.timestamp", tags("job", job), Instant.now().epochSecond)
        if (outcome == Outcome.SUCCESS) {
            setGauge("transittrack.scheduled.job.last.success.timestamp", tags("job", job), Instant.now().epochSecond)
            items?.let { counter("transittrack.scheduled.job.items", tags("job", job)).increment(it.toDouble()) }
        }
    }

    fun outboundHttp(
        purpose: String,
        url: String,
        statusCode: Int?,
        outcome: Outcome,
        elapsed: Duration,
    ) = safely("outbound_http") {
        val tags =
            tags(
                "purpose",
                purpose,
                "host",
                runCatching { URI(url).host ?: "unknown" }.getOrDefault("unknown"),
                "outcome",
                outcome.tag(),
                "status",
                statusCode?.let { "${it / 100}xx" } ?: "none",
            )
        counter("transittrack.http.client.requests", tags).increment()
        timer("transittrack.http.client.duration", tags).record(elapsed)
    }

    private fun increment(
        name: String,
        tags: List<Tag>,
        amount: Int,
    ) = safely(name) { counter(name, tags).increment(amount.toDouble()) }

    private fun counter(
        name: String,
        tags: List<Tag>,
    ): Counter = Counter.builder(name).tags(tags).register(registry)

    private fun timer(
        name: String,
        tags: List<Tag>,
    ): Timer =
        Timer
            .builder(name)
            .publishPercentileHistogram()
            .tags(tags)
            .register(registry)

    private fun setGauge(
        name: String,
        tags: List<Tag>,
        value: Long,
    ) = safely(name) {
        val key = name + tags.joinToString { "${it.key}=${it.value}" }
        val atomic = gauges.computeIfAbsent(key) {
            val created = AtomicLong()
            registry.gauge(name, tags, created)
            created
        }
        atomic.set(value)
    }

    private fun setAgeGauge(
        name: String,
        tags: List<Tag>,
        timestamp: Instant,
    ) = safely(name) {
        val key = name + tags.joinToString { "${it.key}=${it.value}" }
        val atomic = ageTimestamps.computeIfAbsent(key) {
            val created = AtomicLong()
            registry.gauge(name, tags, created) { lastSuccess ->
                (Instant.now().epochSecond - lastSuccess.get()).coerceAtLeast(0).toDouble()
            }
            created
        }
        atomic.set(timestamp.epochSecond)
    }

    private fun avlTags(
        feedCode: String,
        format: String,
        vararg extra: String,
    ): List<Tag> = tags("feed", feedCode, "format", format, *extra)

    private fun tags(vararg values: String): List<Tag> = values.toList().chunked(2).map { Tag.of(it[0], it[1]) }

    private fun safely(
        operation: String,
        block: () -> Unit,
    ) {
        runCatching(block).onFailure {
            Counter
                .builder("transittrack.metrics.collection.failures")
                .tag("operation", "record")
                .register(registry)
                .increment()
            log.debug("failed to record metric operation {}", operation, it)
        }
    }

    private fun Enum<*>.tag(): String = name.lowercase()
}
