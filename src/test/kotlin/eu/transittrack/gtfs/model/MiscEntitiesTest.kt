package eu.transittrack.gtfs.model

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired

@PostgresSliceTest
class MiscEntitiesTest(
    @Autowired val frequencies: GtfsFrequencyRepository,
    @Autowired val translations: GtfsTranslationRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    /** Seed a real feed + revision so the mandated FK on `revision_id` is satisfied (PF-11). */
    private fun seedRevisionId(): Long {
        val feed = feeds.save(
            GtfsFeed(
                code = "f1", name = "F1", description = null, url = "http://x/z.zip",
                pollingCron = null, enabled = true, autoActivate = null, source = FeedSource.API,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        )
        val revision = revisions.save(
            GtfsRevision(feedId = feed.id!!, status = GtfsRevisionStatus.PENDING, sourceUrl = "u")
        )
        return revision.id!!
    }

    @Test
    fun `frequency and translation persist`() {
        val rev = seedRevisionId()

        frequencies.save(GtfsFrequency(rev, "T1", 21600, 36000, 600, 0))
        assertEquals(1, frequencies.findByRevisionIdAndTripId(rev, "T1").size)

        translations.save(GtfsTranslation(rev, "stops", "stop_name", "de", "Hauptbahnhof", "S1", null, null))
        assertEquals("Hauptbahnhof", translations.findByRevisionId(rev)[0].translation)
    }
}
