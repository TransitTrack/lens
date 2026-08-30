package eu.transittrack.gtfs.feed

import eu.transittrack.gtfs.support.PostgresSliceTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@PostgresSliceTest
@Import(GtfsFeedService::class)
class GtfsFeedServiceTest(
    @Autowired val service: GtfsFeedService,
    @Autowired val repo: GtfsFeedRepository,
) {
    private val input = FeedInput("w", "W", null, "http://x/z.zip", null)

    @Test fun `register creates an API feed`() {
        val f = service.register(input)
        assertEquals(FeedSource.API, f.source)
        assertNotNull(repo.findByCode("w"))
    }

    @Test fun `register rejects duplicate code`() {
        service.register(input)
        assertFailsWith<FeedConflictException> { service.register(input) }
    }

    @Test fun `update and delete refuse CONFIG feeds`() {
        repo.save(
            GtfsFeed(
                "c", "C", null, "http://x/z.zip", null, true, null,
                FeedSource.CONFIG, java.time.Instant.now(), java.time.Instant.now(),
            ),
        )
        assertFailsWith<FeedProtectedException> { service.update("c", input.copy(code = "c")) }
        assertFailsWith<FeedProtectedException> { service.delete("c") }
    }
}
