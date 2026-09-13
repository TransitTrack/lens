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

import eu.transittrack.avl.match.MatchOutcome
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.predict.PredictionAlgorithm

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
        feed: AvlFeed,
        outcome: Outcome,
        elapsed: Duration,
    ) = safely("avl_poll_finished") {
        val tags = avlTags(feed, "outcome", outcome.tag())
        counter("transittrack.avl.polls", tags).increment()
        timer("transittrack.avl.poll.duration", tags).record(elapsed)
        if (outcome ==
            Outcome.SUCCESS
        ) {
            val now = Instant.now()
            setGauge("transittrack.avl.feed.last.success.timestamp", tags("feed", feed.code), now.epochSecond)
            setAgeGauge("transittrack.avl.feed.age", tags("feed", feed.code), now)
        }
    }

    fun avlReports(
        feed: AvlFeed,
        decoded: Int,
        accepted: Int,
    ) = safely("avl_reports") {
        val tags = avlTags(feed)
        counter("transittrack.avl.reports", tags + Tag.of("result", "decoded")).increment(decoded.toDouble())
        counter("transittrack.avl.reports", tags + Tag.of("result", "accepted")).increment(accepted.toDouble())
        counter("transittrack.avl.reports", tags + Tag.of("result", "duplicate"))
            .increment((decoded - accepted).coerceAtLeast(0).toDouble())
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
        feed: AvlFeed,
        outcome: Outcome,
        elapsed: Duration,
        count: Int,
    ) = safely("avl_match_batch") {
        val tags = tags("feed", feed.code, "outcome", outcome.tag())
        counter("transittrack.avl.match.batches", tags).increment()
        timer("transittrack.avl.match.batch.duration", tags).record(elapsed)
        DistributionSummary
            .builder("transittrack.avl.match.batch.size")
            .tags(tags)
            .register(registry)
            .record(count.toDouble())
    }

    fun predictionBatch(
        feed: AvlFeed,
        outcome: Outcome,
        elapsed: Duration,
        count: Int,
    ) = safely("prediction_batch") {
        val tags = tags("feed", feed.code, "outcome", outcome.tag())
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

    fun avlReportMatched(
        feed: AvlFeed,
        outcome: MatchMetricOutcome,
        elapsed: Duration,
        match: MatchOutcome?,
    ) = safely("avl_report_matched") {
        val tags = tags("feed", feed.code, "assignment_mode", feed.assignmentMode.tag(), "outcome", outcome.tag())
        counter("transittrack.avl.reports.matched", tags).increment()
        timer("transittrack.avl.match.duration", tags).record(elapsed)
        if (match is MatchOutcome.Matched) {
            DistributionSummary
                .builder("transittrack.avl.match.deviation")
                .tags(tags("feed", feed.code, "assignment_mode", feed.assignmentMode.tag()))
                .baseUnit("meters")
                .register(registry)
                .record(match.deviationM)
            match.score?.let {
                DistributionSummary
                    .builder("transittrack.avl.match.score")
                    .tags(tags("feed", feed.code, "assignment_mode", feed.assignmentMode.tag()))
                    .register(registry)
                    .record(it)
            }
        }
    }

    fun predictionRun(
        feed: AvlFeed,
        algorithm: PredictionAlgorithm,
        outcome: Outcome,
        elapsed: Duration,
        generated: Int,
    ) = safely("prediction_run") {
        val tags = tags("feed", feed.code, "algorithm", algorithm.tag(), "outcome", outcome.tag())
        counter("transittrack.prediction.runs", tags).increment()
        timer("transittrack.prediction.run.duration", tags).record(elapsed)
        if (outcome ==
            Outcome.SUCCESS
        ) {
            counter("transittrack.predictions.generated", tags("feed", feed.code, "algorithm", algorithm.tag()))
                .increment(generated.toDouble())
        }
        setGauge("transittrack.prediction.last.computed.timestamp", tags("feed", feed.code), Instant.now().epochSecond)
    }

    fun predictionCrossings(
        feed: AvlFeed,
        count: Int,
    ) = increment("transittrack.prediction.crossings", tags("feed", feed.code), count)

    fun predictionLearningSamples(
        feed: AvlFeed,
        count: Int,
    ) = increment("transittrack.prediction.learning.samples", tags("feed", feed.code), count)

    fun predictionAccuracy(
        feed: AvlFeed,
        algorithm: PredictionAlgorithm,
        errorSec: Int,
    ) = safely("prediction_accuracy") {
        val tags = tags("feed", feed.code, "algorithm", algorithm.tag())
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
        feed: AvlFeed,
        vararg extra: String,
    ): List<Tag> = tags("feed", feed.code, "format", feed.format.tag(), *extra)

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
