package eu.transittrack.avl.feed

import java.time.Instant

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind
import eu.transittrack.feed.FeedsProperties

/**
 * Upserts the `transittrack.feed.feeds[]` entries that carry an `avl` block into `avl_feed` at
 * startup, mirroring `GtfsFeedConfigSynchronizer`. The `avl_feed` row shares the feed's own code and
 * is linked back to it via `gtfsFeedCode`. Config-sourced rows are updated in place; a code that
 * collides with an API-created row is an [IllegalStateException]. `pruneConfigFeeds` deletes CONFIG
 * rows dropped from config.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlFeedConfigSynchronizer(
    private val repo: AvlFeedRepository,
    private val props: FeedsProperties,
) : ApplicationRunner,
    Ordered {
    override fun getOrder() = Ordered.LOWEST_PRECEDENCE - 100

    @Transactional
    override fun run(args: ApplicationArguments) {
        sync()
    }

    fun sync() {
        val now = Instant.now()
        val avlFeeds = props.feeds.filter { it.avl != null }
        val configCodes = avlFeeds.map { it.code }.toSet()
        for (feed in avlFeeds) {
            val avl = feed.avl!!
            val name = avl.name ?: "${feed.name} vehicle positions"
            val existing = repo.findByCode(feed.code)
            when {
                existing == null -> {
                    repo.save(
                        AvlFeed(
                            code = feed.code,
                            name = name,
                            gtfsFeedCode = feed.code,
                            url = avl.url,
                            format = avl.format,
                            pollIntervalSec = avl.pollIntervalSec,
                            assignmentMode = avl.assignmentMode,
                            predictionAlgorithm = avl.predictionAlgorithm,
                            predictionMode = avl.predictionMode,
                            enabled = avl.enabled,
                            headers = avl.headers.ifEmpty { null },
                            source = AvlFeedSourceKind.CONFIG,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }

                existing.source == AvlFeedSourceKind.API -> {
                    throw IllegalStateException(
                        "config avl feed '${feed.code}' collides with an API-created feed",
                    )
                }

                else -> {
                    existing.name = name
                    existing.gtfsFeedCode = feed.code
                    existing.url = avl.url
                    existing.format = avl.format
                    existing.pollIntervalSec = avl.pollIntervalSec
                    existing.assignmentMode = avl.assignmentMode
                    existing.predictionAlgorithm = avl.predictionAlgorithm
                    existing.predictionMode = avl.predictionMode
                    existing.enabled = avl.enabled
                    existing.headers = avl.headers.ifEmpty { null }
                    existing.updatedAt = now
                    repo.save(existing)
                }
            }
        }
        if (props.pruneConfigFeeds) {
            repo
                .findAll()
                .filter { it.source == AvlFeedSourceKind.CONFIG && it.code !in configCodes }
                .forEach { repo.delete(it) }
        }
    }
}
