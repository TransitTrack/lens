package eu.transittrack.predict.smile

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.predict.PredictionAlgorithm
import eu.transittrack.predict.model.PredictionModelRepository

/**
 * The SMILE_GBM model each AVL feed (keyed by `avl_feed.code`) currently predicts with. Models are
 * immutable and swapped whole in a concurrent map, so a reader sees either the previous or the next
 * model, never a partly built one.
 *
 * Every node holds its own copy: [refresh] (on startup, then every `smile.refresh-ms`) picks up a
 * model trained elsewhere once its stored `trained_at` is newer than the one in memory. No
 * scheduler lock and no role gate: every node that predicts needs it.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class SmileModelStore(
    private val models: PredictionModelRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Entry(
        val model: StopToStopModel,
        val trainedAt: Instant,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    /** `trained_at` of stored payloads that failed to deserialize, so each is tried (and logged) once. */
    private val rejected = ConcurrentHashMap<String, Instant>()

    fun current(feedCode: String): StopToStopModel? = entries[feedCode]?.model

    /** Publishes [model] for [feedCode] unless the model already held was trained after [trainedAt]. */
    fun put(
        feedCode: String,
        model: StopToStopModel,
        trainedAt: Instant = clock.instant(),
    ) {
        entries.compute(feedCode) { _, held -> if (held != null && held.trainedAt.isAfter(trainedAt)) held else Entry(model, trainedAt) }
    }

    /**
     * Loads every stored model trained after the one in memory. A payload that doesn't deserialize is
     * logged and ignored (the in-memory model stays); one feed's failure doesn't stop the others.
     */
    @Scheduled(
        fixedDelayString = "\${transittrack.predict.smile.refresh-ms:300000}",
        initialDelayString = "\${transittrack.predict.smile.refresh-ms:300000}",
    )
    fun refresh() {
        val stored =
            try {
                models.trainedAts(PredictionAlgorithm.SMILE_GBM)
            } catch (e: Exception) {
                log.warn("smile model refresh: could not list stored models", e)
                return
            }
        for ((feedCode, trainedAt) in stored) {
            val held = entries[feedCode]?.trainedAt
            if (held != null && !trainedAt.isAfter(held)) continue
            if (rejected[feedCode] == trainedAt) continue
            try {
                load(feedCode)
            } catch (e: Exception) {
                log.warn("smile model refresh: could not load the model of feed {}", feedCode, e)
            }
        }
    }

    /**
     * The first [refresh]; the scheduled ones start one interval later. Never fails startup: a feed
     * whose model can't be loaded predicts nothing with SMILE_GBM until a later refresh or training.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun refreshOnStartup() {
        try {
            refresh()
        } catch (e: Exception) {
            log.error("smile model refresh on startup failed; retrying on the next scheduled refresh", e)
        }
    }

    private fun load(feedCode: String) {
        val row = models.find(feedCode, PredictionAlgorithm.SMILE_GBM) ?: return
        val model =
            try {
                StopToStopModel.fromBytes(row.payload)
            } catch (e: IllegalArgumentException) {
                rejected[feedCode] = row.trainedAt
                log.warn("smile model refresh: ignoring corrupt model of feed {} trained at {}", feedCode, row.trainedAt, e)
                return
            }
        put(feedCode, model, row.trainedAt)
        log.info("smile model refresh: loaded the model of feed {} trained at {} on {} samples", feedCode, row.trainedAt, row.sampleCount)
    }
}
