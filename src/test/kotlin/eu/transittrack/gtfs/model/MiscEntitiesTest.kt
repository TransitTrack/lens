package eu.transittrack.gtfs.model

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class MiscEntitiesTest(
    @Autowired val frequencies: FrequencyRepository,
    @Autowired val translations: TranslationRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    /** Seed a real feed + revision so the mandated FK on `revision_id` is satisfied (PF-11). */
    private fun seedRevisionId(): Long {
        val feed =
            feeds.save(
                GtfsFeed(
                    code = "f1",
                    name = "F1",
                    description = null,
                    url = "http://x/z.zip",
                    pollingCron = null,
                    enabled = true,
                    autoActivate = null,
                    source = FeedSource.API,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        val revision =
            revisions.save(
                GtfsRevision(
                    feedId = feed.id!!,
                    status = GtfsRevisionStatus.PENDING,
                    sourceUrl = "u",
                ),
            )
        return revision.id!!
    }

    @Test
    fun `frequency and translation persist`() {
        val rev = seedRevisionId()

        frequencies.save(Frequency(rev, "T1", 21600, 36000, 600, 0))
        assertThat(frequencies.findByTripId(rev, "T1")).hasSize(1)

        translations.save(
            Translation(rev, "stops", "stop_name", "de", "Hauptbahnhof", "S1", null, null),
        )
        assertThat(translations.findByRevisionId(rev)[0].translation).isEqualTo("Hauptbahnhof")
    }
}
