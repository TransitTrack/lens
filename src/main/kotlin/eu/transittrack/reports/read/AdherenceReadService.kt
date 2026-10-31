package eu.transittrack.reports.read

import java.time.LocalDate
import kotlin.math.abs

import org.springframework.stereotype.Service

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.reports.model.RouteAdherenceSummary
import eu.transittrack.reports.model.StopPassage
import eu.transittrack.reports.model.TripAdherenceSummary

/**
 * Read-only query layer for the Schedule Adherence reports feature, built on top of
 * [StopPassageDerivationService]'s on-demand per-stop-passage derivation. Exposes both raw
 * per-stop-passage detail ([routeAdherence]/[tripAdherence]/[stopAdherence]) and route/trip-level
 * summary aggregates ([allRoutesAdherence]/[allTripsAdherence]: mean adherence seconds + on-time
 * percentage).
 *
 * "On time" means `abs(adherenceSec) <= ON_TIME_THRESHOLD_SEC`, the single boundary used
 * everywhere in this service - matching the frontend's already-shipped `adherenceBadge` success
 * cutoff. [StopPassage.adherenceSec] is `null` for stops the AVL match history never actually
 * covers (see [StopPassage]'s KDoc); such rows are excluded from a summary's sample entirely,
 * never treated as a zero-adherence observation. A route or trip execution with zero non-null
 * samples produces no summary row at all, so summaries never divide by zero.
 */
@Service
@ConditionalOnRole(Role.API)
class AdherenceReadService(
    private val derivation: StopPassageDerivationService,
    private val routes: RouteRepository,
    private val avlFeeds: AvlFeedRepository,
    private val revisionResolver: RevisionResolver,
) {
    companion object {
        const val ON_TIME_THRESHOLD_SEC = 60
    }

    fun routeAdherence(
        feedCode: String,
        routeId: String,
        startDate: String,
        endDate: String,
    ): List<StopPassage> {
        val (start, end) = parseRange(startDate, endDate)
        return derivation.derive(feedCode, start, end, routeId = routeId)
    }

    fun tripAdherence(
        feedCode: String,
        tripId: String,
        startDate: String,
        endDate: String,
    ): List<StopPassage> {
        val (start, end) = parseRange(startDate, endDate)
        return derivation.derive(feedCode, start, end, tripId = tripId)
    }

    fun stopAdherence(
        feedCode: String,
        stopId: String,
        startDate: String,
        endDate: String,
    ): List<StopPassage> {
        val (start, end) = parseRange(startDate, endDate)
        // StopPassageDerivationService.derive has no stop-id filter of its own, so derive across
        // the whole feed/window and filter down to the requested stop here.
        return derivation.derive(feedCode, start, end).filter { it.stopId == stopId }
    }

    fun allRoutesAdherence(
        feedCode: String,
        startDate: String,
        endDate: String,
    ): List<RouteAdherenceSummary> {
        val (start, end) = parseRange(startDate, endDate)
        val revisionId = resolveRevisionId(feedCode)

        return derivation
            .derive(feedCode, start, end)
            .groupBy { it.routeId }
            .mapNotNull { (routeId, passages) -> summarizeRoute(routeId, passages, revisionId) }
            .sortedBy { it.routeId }
    }

    fun allTripsAdherence(
        feedCode: String,
        routeId: String?,
        startDate: String,
        endDate: String,
    ): List<TripAdherenceSummary> {
        val (start, end) = parseRange(startDate, endDate)
        return derivation
            .derive(feedCode, start, end, routeId = routeId)
            .groupBy { it.tripId to it.serviceDate }
            .mapNotNull { (key, passages) -> summarizeTrip(key.first, key.second, passages) }
            .sortedWith(compareBy({ it.tripId }, { it.serviceDate }))
    }

    private fun resolveRevisionId(feedCode: String): Long {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        return revisionResolver.resolve(feed.gtfsFeedCode, null)
    }

    private fun summarizeRoute(
        routeId: String,
        passages: List<StopPassage>,
        revisionId: Long,
    ): RouteAdherenceSummary? {
        val samples = passages.mapNotNull { it.adherenceSec }
        if (samples.isEmpty()) return null
        return RouteAdherenceSummary(
            routeId = routeId,
            routeShortName = routes.findByRouteId(revisionId, routeId)?.routeShortName,
            sampleCount = samples.size,
            meanAdherenceSec = samples.average(),
            onTimePct = onTimePct(samples),
        )
    }

    private fun summarizeTrip(
        tripId: String,
        serviceDate: LocalDate,
        passages: List<StopPassage>,
    ): TripAdherenceSummary? {
        val samples = passages.mapNotNull { it.adherenceSec }
        if (samples.isEmpty()) return null
        return TripAdherenceSummary(
            tripId = tripId,
            routeId = passages.first().routeId,
            serviceDate = serviceDate,
            sampleCount = samples.size,
            meanAdherenceSec = samples.average(),
            onTimePct = onTimePct(samples),
        )
    }

    private fun onTimePct(samples: List<Int>): Double = 100.0 * samples.count { abs(it) <= ON_TIME_THRESHOLD_SEC } / samples.size

    private fun parseRange(
        startDate: String,
        endDate: String,
    ): Pair<LocalDate, LocalDate> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        require(!end.isBefore(start)) { "end date must not be before start date" }
        return start to end
    }
}
