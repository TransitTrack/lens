package eu.transittrack.schedule.read

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

import eu.transittrack.gtfs.download.FeedDownloader
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.support.FixtureDownloader
import eu.transittrack.support.PostgresPerClassTest

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(StopScheduleReadServiceTest.Stub::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StopScheduleReadServiceTest(
    @Autowired val service: StopScheduleReadService,
    @Autowired val feedService: GtfsFeedService,
    @Autowired val ingestion: IngestionService,
) : PostgresPerClassTest() {
    @TestConfiguration(proxyBeanMethods = false)
    class Stub {
        @Bean
        @Primary
        fun dl(): FeedDownloader = FixtureDownloader("schedule-sample")
    }

    @BeforeAll
    fun ingest() {
        truncateBeforeFixture()
        feedService.register(FeedInput("g", "G", null, "http://x/g.zip", null))
        ingestion.ingestBlocking("g")
    }

    @Test
    fun `lists every pattern calling at the stop with its clock-time departures and the days its services run`() {
        val schedule = service.stopSchedule("g", "S2", null)

        assertThat(schedule).isNotNull()
        val calls = schedule!!.calls
        // S2 is on the outbound pattern (T1, T2), the inbound one (T4) and the frequency-based one (T5).
        assertThat(calls.size).isEqualTo(3)
        assertThat(calls.map { it.routeId }.distinct()).isEqualTo(listOf("RA"))
        assertThat(calls.all { it.routeShortName == "A" }).isEqualTo(true)

        val departures = calls.flatMap { it.departures }
        assertThat(departures.map { it.departureSec }).contains(8 * 3600 + 10 * 60) // T1 at S2
        assertThat(departures.map { it.departureSec }).contains(9 * 3600) // T4 at S2
        assertThat(departures.all { it.serviceId == "WK" }).isEqualTo(true)
        // The frequency-based trip has no clock times, so its pattern calls here but lists no departure.
        assertThat(calls.count { it.departures.isEmpty() }).isEqualTo(1)

        assertThat(schedule.services.map { it.serviceId }).isEqualTo(listOf("WK"))
        val weekday = schedule.services.single()
        assertThat(weekday.weekday).isEqualTo(true)
        assertThat(weekday.saturday).isEqualTo(false)
        assertThat(weekday.sunday).isEqualTo(false)
    }

    @Test
    fun `departures within a call are earliest first`() {
        val calls = service.stopSchedule("g", "S1", null)!!.calls

        calls.forEach { call ->
            assertThat(call.departures.map { it.departureSec }).isEqualTo(call.departures.map { it.departureSec }.sorted())
        }
    }

    @Test
    fun `an unknown stop is null rather than an empty schedule`() {
        assertThat(service.stopSchedule("g", "nope", null)).isNull()
    }
}
