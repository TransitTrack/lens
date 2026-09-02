package eu.transittrack.gtfs.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
class FaresEntitiesTest(
    @Autowired val products: FareProductRepository,
    @Autowired val areas: AreaRepository,
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
) {
    /** Seed a real feed + revision so the mandated FK on `revision_id` is satisfied (PF-11). */
    private fun seedRevisionIds(count: Int): List<Long> {
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
        return (1..count).map {
            revisions
                .save(
                    GtfsRevision(
                        feedId = feed.id!!,
                        status = GtfsRevisionStatus.PENDING,
                        sourceUrl = "u",
                    ),
                ).id!!
        }
    }

    @Test
    fun `fare product and area persist and query by revision`() {
        val (rev1, rev2) = seedRevisionIds(2)

        products.save(FareProduct(rev1, "P1", "Single", null, null, 3.40, "PLN"))
        products.save(FareProduct(rev2, "P1", "Single", null, null, 3.60, "PLN"))
        assertEquals(1, products.findByRevisionId(rev1).size)

        areas.save(Area(rev1, "AREA1", "Centre"))
        assertEquals("Centre", areas.findByRevisionId(rev1)[0].areaName)
    }
}
