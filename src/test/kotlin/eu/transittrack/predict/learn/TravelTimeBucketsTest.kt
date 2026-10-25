package eu.transittrack.predict.learn

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class TravelTimeBucketsTest {
    private val zone = ZoneId.of("Europe/Bucharest")
    private val monday = LocalDate.of(2026, 10, 26)

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = TravelTimeBucket.at(
        date, zone,
        date
            .atTime(hour % 24, minute)
            .atZone(zone)
            .toInstant()
            .plusSeconds(if (hour >= 24) 86_400 else 0),
    )

    @Test
    fun `day type follows the service date's day of week`() {
        assertThat(DayType.of(monday)).isEqualTo(DayType.WEEKDAY)
        assertThat(DayType.of(monday.plusDays(5))).isEqualTo(DayType.SATURDAY)
        assertThat(DayType.of(monday.plusDays(6))).isEqualTo(DayType.SUNDAY)
    }

    @Test
    fun `time bands split the service day at 6, 9, 15 and 19`() {
        assertThat(at(monday, 5, 59).timeBand).isEqualTo(TimeBand.NIGHT)
        assertThat(at(monday, 6).timeBand).isEqualTo(TimeBand.MORNING_PEAK)
        assertThat(at(monday, 9).timeBand).isEqualTo(TimeBand.MIDDAY)
        assertThat(at(monday, 15).timeBand).isEqualTo(TimeBand.AFTERNOON_PEAK)
        assertThat(at(monday, 19).timeBand).isEqualTo(TimeBand.EVENING)
        assertThat(at(monday, 23, 59).timeBand).isEqualTo(TimeBand.EVENING)
    }

    @Test
    fun `a trip running past midnight stays on its service day, in the night band`() {
        val bucket = at(monday, 24, 30) // 00:30 on Tuesday, Monday's service day
        assertThat(bucket).isEqualTo(TravelTimeBucket(DayType.WEEKDAY, TimeBand.NIGHT))
    }

    @Test
    fun `a sample updates its bucket, its day type and the overall roll-up`() {
        assertThat(TravelTimeBucket(DayType.SATURDAY, TimeBand.EVENING).withRollups()).containsExactly(
            TravelTimeBucket(DayType.SATURDAY, TimeBand.EVENING),
            TravelTimeBucket(DayType.SATURDAY, TimeBand.ALL),
            TravelTimeBucket.OVERALL,
        )
    }

    private val peak = TravelTimeBucket(DayType.WEEKDAY, TimeBand.MORNING_PEAK)
    private val weekday = TravelTimeBucket(DayType.WEEKDAY, TimeBand.ALL)

    private fun pick(values: Map<TravelTimeBucket, Long>) = pickLearned(values, peak, minSamples = 10) { it }

    @Test
    fun `the exact bucket wins once it has enough samples`() {
        assertThat(pick(mapOf(peak to 10L, weekday to 500L, TravelTimeBucket.OVERALL to 900L))).isEqualTo(10L)
    }

    @Test
    fun `a thin bucket falls back to its day type, then to the overall value`() {
        assertThat(pick(mapOf(peak to 3L, weekday to 40L, TravelTimeBucket.OVERALL to 900L))).isEqualTo(40L)
        assertThat(pick(mapOf(peak to 3L, weekday to 5L, TravelTimeBucket.OVERALL to 2L))).isEqualTo(2L)
    }

    @Test
    fun `nothing learned at all falls back to the schedule`() {
        assertThat(pick(emptyMap())).isNull()
    }
}
