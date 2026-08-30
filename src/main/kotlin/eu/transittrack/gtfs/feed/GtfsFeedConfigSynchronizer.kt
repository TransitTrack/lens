package eu.transittrack.gtfs.feed

import eu.transittrack.gtfs.config.GtfsProperties
import java.time.Instant
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GtfsFeedConfigSynchronizer(
    private val repo: GtfsFeedRepository,
    private val props: GtfsProperties,
) : ApplicationRunner, Ordered {

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE - 100

    override fun run(args: ApplicationArguments) {
        sync()
    }

    @Transactional
    fun sync() {
        val now = Instant.now()
        val configCodes = props.feeds.map { it.code }.toSet()
        for (def in props.feeds) {
            val existing = repo.findByCode(def.code)
            when {
                existing == null -> repo.save(
                    GtfsFeed(
                        code = def.code,
                        name = def.name,
                        description = def.description,
                        url = def.url,
                        pollingCron = def.pollingCron,
                        enabled = def.enabled,
                        autoActivate = def.autoActivate,
                        source = FeedSource.CONFIG,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                existing.source == FeedSource.API ->
                    throw FeedConflictException(
                        "config feed '${def.code}' collides with an API-created feed",
                    )

                else -> {
                    existing.name = def.name
                    existing.description = def.description
                    existing.url = def.url
                    existing.pollingCron = def.pollingCron
                    existing.enabled = def.enabled
                    existing.autoActivate = def.autoActivate
                    existing.updatedAt = now
                    repo.save(existing)
                }
            }
        }
        if (props.pruneConfigFeeds) {
            repo.findAll()
                .filter { it.source == FeedSource.CONFIG && it.code !in configCodes }
                .forEach { repo.delete(it) }
        }
    }
}
