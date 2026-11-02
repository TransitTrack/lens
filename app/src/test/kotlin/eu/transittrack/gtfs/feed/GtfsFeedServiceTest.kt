package eu.transittrack.gtfs.feed

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
@Import(GtfsFeedService::class)
class GtfsFeedServiceTest(
    @Autowired val service: GtfsFeedService,
    @Autowired val repo: GtfsFeedRepository,
) : PostgresPerMethodTest() {
    private val input = FeedInput("w", "W", null, "http://x/z.zip", null)

    @Test
    fun `register creates an API feed`() {
        val f = service.register(input)
        assertThat(f.source).isEqualTo(FeedSource.API)
        assertThat(repo.findByCode("w")).isNotNull()
    }

    @Test
    fun `register rejects duplicate code`() {
        service.register(input)
        assertFailure { service.register(input) }.isInstanceOf<FeedConflictException>()
    }

    @Test
    fun `update and delete allow mutation of CONFIG feeds`() {
        repo.save(
            GtfsFeed(
                "c",
                "C",
                null,
                "http://x/z.zip",
                null,
                true,
                null,
                FeedSource.CONFIG,
                java.time.Instant.now(),
                java.time.Instant.now(),
            ),
        )

        val updated = service.update("c", input.copy(code = "c"))
        assertThat(updated.name).isEqualTo(input.name)

        assertThat(service.delete("c")).isTrue()
        assertThat(repo.findByCode("c")).isNull()
    }
}
