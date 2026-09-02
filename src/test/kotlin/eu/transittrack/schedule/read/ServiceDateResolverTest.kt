package eu.transittrack.schedule.read

import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Calendar
import eu.transittrack.gtfs.model.CalendarDate
import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.support.PostgresSliceTest

@PostgresSliceTest
@Import(ServiceDateResolver::class)
class ServiceDateResolverTest(
    @Autowired val feeds: GtfsFeedRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val calendars: CalendarRepository,
    @Autowired val calendarDates: CalendarDateRepository,
    @Autowired val resolver: ServiceDateResolver,
) {
    private var rev: Long = 0

    @BeforeTest
    fun seed() {
        val feed =
            feeds.save(
                GtfsFeed(
                    "sdr",
                    "SDR",
                    null,
                    "http://x/z.zip",
                    null,
                    true,
                    null,
                    FeedSource.API,
                    Instant.now(),
                    Instant.now(),
                ),
            )
        rev =
            revisions
                .save(
                    GtfsRevision(
                        feedId = feed.id!!,
                        status = GtfsRevisionStatus.READY,
                        sourceUrl = "http://x/z.zip",
                        createdAt = Instant.now(),
                    ),
                ).id!!
        // WK: Mon-Fri, Jan-Dec 2026
        calendars.save(
            Calendar(
                rev,
                "WK",
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
            ),
        )
        // SAT: Sat only
        calendars.save(
            Calendar(
                rev,
                "SAT",
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
            ),
        )
        // exceptions: remove WK on Tue 2026-01-06, add SAT on Sun 2026-01-04
        calendarDates.save(CalendarDate(rev, "WK", LocalDate.of(2026, 1, 6), 2))
        calendarDates.save(CalendarDate(rev, "SAT", LocalDate.of(2026, 1, 4), 1))
    }

    @Test
    fun `weekday resolves calendar`() {
        assertEquals(
            setOf("WK"),
            resolver.activeServiceIds(rev, LocalDate.of(2026, 1, 5)),
        ) // Monday
    }

    @Test
    fun `saturday resolves SAT`() {
        assertEquals(setOf("SAT"), resolver.activeServiceIds(rev, LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun `removal exception drops the service`() {
        assertEquals(
            emptySet(),
            resolver.activeServiceIds(rev, LocalDate.of(2026, 1, 6)),
        ) // Tue, WK removed
    }

    @Test
    fun `add exception injects the service`() {
        assertEquals(
            setOf("SAT"),
            resolver.activeServiceIds(rev, LocalDate.of(2026, 1, 4)),
        ) // Sun, SAT added
    }

    @Test
    fun `date outside the calendar window is inactive`() {
        assertEquals(emptySet(), resolver.activeServiceIds(rev, LocalDate.of(2025, 12, 31)))
    }
}
