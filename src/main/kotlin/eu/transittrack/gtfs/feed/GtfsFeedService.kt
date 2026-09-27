package eu.transittrack.gtfs.feed

import java.time.Instant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class FeedConflictException(
    msg: String,
) : RuntimeException(msg)

class FeedProtectedException(
    msg: String,
) : RuntimeException(msg)

class FeedNotFoundException(
    code: String,
) : RuntimeException("no feed '$code'")

data class FeedInput(
    val code: String,
    val name: String,
    val description: String?,
    val url: String,
    val pollingCron: String?,
    val enabled: Boolean = true,
    val autoActivate: Boolean? = null,
)

@Service
class GtfsFeedService(
    private val repo: GtfsFeedRepository,
) {
    @Transactional
    fun register(input: FeedInput): GtfsFeed {
        if (repo.findByCode(input.code) != null) {
            throw FeedConflictException("feed '${input.code}' exists")
        }
        val now = Instant.now()
        return repo.save(
            GtfsFeed(
                code = input.code,
                name = input.name,
                description = input.description,
                url = input.url,
                pollingCron = input.pollingCron,
                enabled = input.enabled,
                autoActivate = input.autoActivate,
                source = FeedSource.API,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun update(
        code: String,
        input: FeedInput,
    ): GtfsFeed {
        val feed = repo.findByCode(code) ?: throw FeedNotFoundException(code)
        if (feed.source == FeedSource.CONFIG) {
            throw FeedProtectedException("feed '$code' is config-managed")
        }
        feed.name = input.name
        feed.description = input.description
        feed.url = input.url
        feed.pollingCron = input.pollingCron
        feed.enabled = input.enabled
        feed.autoActivate = input.autoActivate
        feed.updatedAt = Instant.now()
        return repo.save(feed)
    }

    @Transactional
    fun delete(code: String): Boolean {
        val feed = repo.findByCode(code) ?: return false
        if (feed.source == FeedSource.CONFIG) {
            throw FeedProtectedException("feed '$code' is config-managed")
        }
        repo.delete(feed)
        return true
    }

    fun get(code: String): GtfsFeed? = repo.findByCode(code)

    fun list(): List<GtfsFeed> = repo.findAll()

    fun codeOf(feedId: Long): String? = repo.findById(feedId).map { it.code }.orElse(null)

    @Transactional
    fun markIngested(feedId: Long) {
        repo.findById(feedId).ifPresent {
            it.lastIngestAt = Instant.now()
            repo.save(it)
        }
    }
}
