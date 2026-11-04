package eu.transittrack.predict.learn

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import eu.transittrack.avl.match.serviceDayStart

/**
 * Where learned travel times live: a trip pattern's stable `pattern_key` (route, shape, ordered
 * stops) within its GTFS feed, not the pattern's per-revision row id - so learning carries over to
 * a new revision that keeps the pattern and only changes its times.
 */
data class LearningKey(
    val gtfsFeedCode: String,
    val patternKey: String,
)

/** Stored as its ordinal (`day_type` SMALLINT); [ALL] must stay last - migration 0017 writes its ordinal. */
enum class DayType {
    WEEKDAY,
    SATURDAY,
    SUNDAY,
    ALL,
    ;

    companion object {
        /** Public holidays aren't known here: they count as the weekday they fall on. */
        fun of(serviceDate: LocalDate): DayType =
            when (serviceDate.dayOfWeek) {
                DayOfWeek.SATURDAY -> SATURDAY
                DayOfWeek.SUNDAY -> SUNDAY
                else -> WEEKDAY
            }
    }
}

/** Stored as its ordinal (`time_band` SMALLINT); [ALL] must stay last - migration 0017 writes its ordinal. */
enum class TimeBand(
    val fromHour: Int,
) {
    NIGHT(0),
    MORNING_PEAK(6),
    MIDDAY(9),
    AFTERNOON_PEAK(15),
    EVENING(19),
    ALL(24),
    ;

    companion object {
        /** Service-day seconds past 24:00 (a trip running after midnight) wrap into the night band. */
        fun of(serviceSec: Long): TimeBand {
            val hour = Math.floorMod(serviceSec / 3600, 24L).toInt()
            return entries.last { it != ALL && it.fromHour <= hour }
        }
    }
}

/** A day type and time band; learned values are kept per bucket plus two coarser roll-ups. */
data class TravelTimeBucket(
    val dayType: DayType,
    val timeBand: TimeBand,
) {
    /** This bucket, its whole day type, then everything: each learning sample updates all three. */
    fun withRollups(): List<TravelTimeBucket> = listOf(this, TravelTimeBucket(dayType, TimeBand.ALL), OVERALL)

    companion object {
        val OVERALL = TravelTimeBucket(DayType.ALL, TimeBand.ALL)

        /** The bucket of a moment on a service day: its day type, and its time band by service-day clock. */
        fun at(
            serviceDate: LocalDate,
            zone: ZoneId,
            ts: Instant,
        ): TravelTimeBucket =
            TravelTimeBucket(DayType.of(serviceDate), TimeBand.of(Duration.between(serviceDayStart(serviceDate, zone), ts).seconds))
    }
}

/**
 * The most specific learned value for [bucket]: the bucket itself, else its whole day type - each
 * only with at least [minSamples] samples - else the overall value, whatever its sample count
 * (better than the schedule seed once anything at all has been observed).
 */
fun <T> pickLearned(
    values: Map<TravelTimeBucket, T>,
    bucket: TravelTimeBucket,
    minSamples: Int,
    sampleCount: (T) -> Long,
): T? {
    values[bucket]?.takeIf { sampleCount(it) >= minSamples }?.let { return it }
    values[TravelTimeBucket(bucket.dayType, TimeBand.ALL)]?.takeIf { sampleCount(it) >= minSamples }?.let { return it }
    return values[TravelTimeBucket.OVERALL]
}
