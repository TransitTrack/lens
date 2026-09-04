package eu.transittrack.avl.feed

import java.time.Instant

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.AvlFeed
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlFeedSourceKind

/**
 * Upserts `transittrack.avl.feeds[]` config entries into `avl_feed` at startup, mirroring
 * `GtfsFeedConfigSynchronizer`. Config-sourced rows are updated in place; a code that collides with
 * an API-created row is an [IllegalStateException]. `pruneConfigFeeds` deletes CONFIG rows dropped
 * from config.
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlFeedConfigSynchronizer(
    private val repo: AvlFeedRepository,
    private val props: AvlProperties,
) : ApplicationRunner,
    Ordered {
    override fun getOrder() = Ordered.LOWEST_PRECEDENCE - 100

    @Transactional
    override fun run(args: ApplicationArguments) {
        sync()
    }

    fun sync() {
        val now = Instant.now()
        val configCodes = props.feeds.map { it.code }.toSet()
        for (def in props.feeds) {
            val existing = repo.findByCode(def.code)
            when {
                existing == null -> {
                    repo.save(
                        AvlFeed(
                            code = def.code,
                            name = def.name,
                            gtfsFeedCode = def.gtfsFeedCode,
                            url = def.url,
                            format = def.format,
                            pollIntervalSec = def.pollIntervalSec,
                            assignmentMode = def.assignmentMode,
                            enabled = def.enabled,
                            headers = def.headers.ifEmpty { null },
                            source = AvlFeedSourceKind.CONFIG,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }

                existing.source == AvlFeedSourceKind.API -> {
                    throw IllegalStateException(
                        "config avl feed '${def.code}' collides with an API-created feed",
                    )
                }

                else -> {
                    existing.name = def.name
                    existing.gtfsFeedCode = def.gtfsFeedCode
                    existing.url = def.url
                    existing.format = def.format
                    existing.pollIntervalSec = def.pollIntervalSec
                    existing.assignmentMode = def.assignmentMode
                    existing.enabled = def.enabled
                    existing.headers = def.headers.ifEmpty { null }
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
