package eu.transittrack.gtfs.model

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class AccessibilityEntitiesTest(
    @Autowired val locations: LocationRepository,
    @Autowired val pathways: PathwayRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) : PostgresPerMethodTest() {
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
    fun `location stores geojson geometry, pathway persists`() {
        val rev = seedRevisionId()

        locations.save(
            Location(
                rev,
                "L1",
                "Zone A",
                null,
                """{"type":"Polygon","coordinates":[[[17.0,51.0],[17.1,51.0],[17.1,51.1],[17.0,51.0]]]}""",
            ),
        )
        assertThat(
            locations.findByRevisionId(rev)[0].geometry.let {
                JsonMapper
                    .builder()
                    .build()
                    .readTree(it)
                    .get("type")
                    .asString()
            },
        ).isEqualTo("Polygon")

        pathways.save(Pathway(rev, "PW1", "S1", "S2", 1, 1, null, 30, null, null, null, null, null))
        assertThat(pathways.findByRevisionId(rev)).hasSize(1)
    }
}
