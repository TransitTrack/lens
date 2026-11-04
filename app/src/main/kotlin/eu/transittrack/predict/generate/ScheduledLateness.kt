package eu.transittrack.predict.generate

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.avl.match.MatchContext
import eu.transittrack.predict.PredictProperties
import eu.transittrack.schedule.model.ServiceStopTime
import eu.transittrack.schedule.model.TripRef

/** What an arrival at a trip's stop is judged late against: its scheduled arrival, and the scheduled headway there if any. */
data class LatenessReference(
    val scheduledArrival: Instant,
    val scheduledHeadwaySec: Int?,
)

/**
 * The lateness policy and the schedule it is applied against, shared by the SMILE_GBM assessment of
 * a predicted arrival and the scoring of the actual one, so both use the same scheduled arrival and
 * the same margin. Everything is read from the match context's cached schedule.
 */
@Component
@ConditionalOnProperty("transittrack.predict.enabled", havingValue = "true")
class ScheduledLateness(
    props: PredictProperties,
) {
    val policy = LatenessPolicy(props.late.scheduleToleranceSec, props.late.headwayFraction)

    private data class RouteDay(
        val revisionId: Long,
        val routeId: String,
        val directionId: Int?,
        val serviceDate: LocalDate,
    )

    /**
     * Clock-time trips' scheduled times by stop id, per route, direction and service date. Bounded by
     * the stop times it holds ([MAX_CACHED_STOP_TIMES]), not by entries: one busy route-day holds
     * thousands of them, a quiet one a few dozen.
     */
    private val stopTimesByRouteDay: Cache<RouteDay, Map<String, List<ServiceStopTime>>> =
        Caffeine
            .newBuilder()
            .maximumWeight(MAX_CACHED_STOP_TIMES)
            .weigher<RouteDay, Map<String, List<ServiceStopTime>>> { _, byStop -> weightOf(byStop) }
            .expireAfterAccess(Duration.ofHours(2))
            .build()

    /**
     * `null` for a frequency-based trip, or a stop without a scheduled time: the delay is unknown. The
     * scheduled arrival is the stop's arrival time, else its departure time, as the headway uses.
     */
    fun reference(
        ctx: MatchContext,
        serviceDate: LocalDate,
        trip: TripRef?,
        stopPathIndex: Int,
        stopIds: PatternStopIds = PatternStopIds(ctx),
    ): LatenessReference? {
        if (trip == null || trip.frequencyBased == true) return null
        val point = ctx.scheduleOf(trip.id).firstOrNull { it.stopPathIndex == stopPathIndex } ?: return null
        val scheduledArrival =
            (point.arrivalSec ?: point.departureSec)?.let { serviceSecToInstant(serviceDate, it, ctx.zone) } ?: return null
        return LatenessReference(scheduledArrival, scheduledHeadwaySec(ctx, serviceDate, trip, stopIds, stopPathIndex, scheduledArrival))
    }

    fun assess(
        reference: LatenessReference,
        predictedArrival: Instant,
        sigmaSec: Double,
    ): LateAssessment? = assessLateness(policy, reference.scheduledArrival, predictedArrival, sigmaSec, reference.scheduledHeadwaySec)

    fun isLate(
        reference: LatenessReference,
        arrival: Instant,
    ): Boolean = isLateArrival(policy, reference.scheduledArrival, arrival, reference.scheduledHeadwaySec)

    /**
     * The scheduled headway at the stop [trip] reaches at [stopPathIndex], around [scheduledArrival]:
     * the same average gap, over the same clock-time trips of the route and direction at that stop,
     * that the `headway` read model computes ([averageScheduledHeadwaySec]), read from the match
     * context's cached schedule rather than the database.
     */
    private fun scheduledHeadwaySec(
        ctx: MatchContext,
        serviceDate: LocalDate,
        trip: TripRef,
        stopIds: PatternStopIds,
        stopPathIndex: Int,
        scheduledArrival: Instant,
    ): Int? {
        val patternId = trip.tripPatternId ?: return null
        val stopId = stopIds.of(patternId)[stopPathIndex] ?: return null
        val stopTimes =
            stopTimesByRouteDay
                .get(RouteDay(ctx.revisionId, trip.routeId, trip.directionId, serviceDate)) { stopTimesAtStops(ctx, it) }[stopId]
                ?: return null
        return averageScheduledHeadwaySec(
            stopTimes,
            scheduledArrival.minus(SCHEDULED_HEADWAY_MARGIN),
            scheduledArrival.plus(SCHEDULED_HEADWAY_MARGIN),
            ctx.zone,
            ctx::activeServiceIds,
        )
    }

    /**
     * Every clock-time trip of the route (and direction, when known) that can run in a headway
     * window around [RouteDay.serviceDate]'s times, by the stops it serves. Service-day times run
     * past 24:00, and the window reaches an hour either side, so services of two days before and
     * after count; [averageScheduledHeadwaySec] keeps only those running on each date.
     */
    private fun stopTimesAtStops(
        ctx: MatchContext,
        key: RouteDay,
    ): Map<String, List<ServiceStopTime>> {
        val services = (-2L..2L).flatMapTo(HashSet()) { ctx.activeServiceIds(key.serviceDate.plusDays(it)) }
        if (services.isEmpty()) return emptyMap()
        val stopIds = PatternStopIds(ctx)
        val out = HashMap<String, MutableList<ServiceStopTime>>()
        for (t in ctx.candidateTripsForRoute(key.routeId, services)) {
            if (t.frequencyBased == true) continue
            if (key.directionId != null && t.directionId != key.directionId) continue
            val patternId = t.tripPatternId ?: continue
            val stopIdAt = stopIds.of(patternId)
            for (point in ctx.scheduleOf(t.id)) {
                val stopId = stopIdAt[point.stopPathIndex] ?: continue
                out.getOrPut(stopId) { ArrayList() }.add(ServiceStopTime(point.arrivalSec, point.departureSec, t.serviceId))
            }
        }
        return out
    }
}

/** A cached route-day's weight: its stop times, plus one per stop for the map entry. */
internal fun weightOf(byStop: Map<String, List<ServiceStopTime>>): Int =
    (byStop.size.toLong() + byStop.values.sumOf { it.size.toLong() }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

/**
 * The [ScheduledLateness] headway cache's bound, in stop times: measured at about 60 bytes each (a
 * [ServiceStopTime] with its two boxed times and its list slot; the service id is shared with the
 * schedule), some 30 MB. That holds the current route-days of a feed with about a hundred busy
 * route-directions (~5 000 stop times each); a larger one recomputes the least recently used from
 * the cached schedule.
 */
internal const val MAX_CACHED_STOP_TIMES = 500_000L

/** Each pattern's stop ids by stop path index, read from [ctx] once per use. */
class PatternStopIds(
    private val ctx: MatchContext,
) {
    private val byPattern = HashMap<Long, Map<Int, String>>()

    fun of(patternId: Long): Map<Int, String> =
        byPattern.getOrPut(patternId) { ctx.patternStops(patternId).associate { it.stopPathIndex to it.stopId } }
}
