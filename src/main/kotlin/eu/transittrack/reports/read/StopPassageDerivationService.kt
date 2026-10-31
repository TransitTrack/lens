package eu.transittrack.reports.read

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

import org.springframework.stereotype.Service

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.ingest.AvlOccupancyStatus
import eu.transittrack.avl.match.AvlMatchContextFactory
import eu.transittrack.avl.match.AvlReportView
import eu.transittrack.avl.match.TemporalMatcher
import eu.transittrack.avl.match.inferServiceDate
import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportRowRepository
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.reports.model.StopPassage
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPath
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripRef

/**
 * How close (in metres) a match's `distanceAlongTripM` must be to a pattern's total shape length to
 * count as having reached the pattern's FINAL scheduled stop. `SpatialMatcher.stopPathIndexAt`
 * clamps its returned index to the last stop-path index, so the terminal stop can never be confirmed
 * by the strict `stopPathIndex > lastIndex` rule used for every other stop - this distance-based
 * check is the substitute for that one stop. Sized similarly to [eu.transittrack.avl.AvlProperties]'s
 * `Match.backtrackToleranceM`/`maxDeviationM` (tens of metres) to absorb ordinary GPS/shape-
 * projection noise without masking a vehicle that genuinely stopped short of the final stop.
 */
private const val TERMINAL_STOP_TOLERANCE_M = 20.0

/**
 * Derives per-stop schedule adherence ("stop passages") from persisted AVL [VehicleMatch] history
 * - on demand, per query, with no new persisted table. For every scheduled stop of every trip
 * execution in the requested window, this reports whether the vehicle arrived on time, early,
 * late, or never reached that stop at all (`observedSec`/`adherenceSec` both `null`).
 *
 * "On time" math itself is not recomputed here: [VehicleMatch.scheduleAdherenceSec] is already
 * computed and persisted per AVL ping during live matching: this service only reuses that stored
 * value for whichever match is picked as the observed passage of a stop.
 *
 * Known Phase 1 limitations (not bugs - deliberately out of scope for this task):
 * - **Cross-midnight trips**: a trip execution is grouped by its *inferred* `serviceDate`
 *   ([inferServiceDate]), and the final result is filtered to `[startDate, endDate]` by that same
 *   service date - with no stitching across the midnight boundary and no widening of the
 *   underlying match-fetch window by trip duration. A stop whose actual ping timestamp falls on
 *   the "other side" of midnight from its trip's inferred service date can therefore come back as
 *   unreached (`null`) instead of populated when queried right at a range boundary. That is a
 *   missing-data outcome, not a corrupted one, and deep overnight-trip handling is a non-goal of
 *   this phase.
 * - **Frequency-based trips**: a single `trip_row_id` that runs multiple times in a day (GTFS
 *   `frequencies.txt`) is not specially handled - grouping by `(tripRowId, serviceDate, vehicleId)`
 *   happens to separate most such re-runs when different vehicles serve them, but two runs of the
 *   same frequency-based trip by the *same* vehicle on the same day would still collapse into one
 *   group. Not addressed in Phase 1.
 * - **Revision drift (route/trip id resolution only)**: resolving a `routeId`/`tripId` filter to
 *   trip row ids (via [RevisionResolver]) still always uses the feed's *currently active*
 *   revision, so a `routeId`/`tripId` that only existed in an older revision (and not the current
 *   one) won't resolve. This is deliberately out of scope for Phase 1. The per-group
 *   schedule/stop-path lookups below are NOT subject to this limitation: they use each matched
 *   trip's own `revisionId`, not the globally-resolved one, specifically so a historical
 *   [VehicleMatch] from an older revision still joins against the schedule/pattern shape it was
 *   actually matched under.
 */
@Service
@ConditionalOnRole(Role.API)
class StopPassageDerivationService(
    private val avlFeeds: AvlFeedRepository,
    private val vehicleMatches: VehicleMatchRepository,
    private val avlReports: AvlReportRowRepository,
    private val trips: TripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val stopPaths: StopPathRepository,
    private val stops: StopRepository,
    private val revisionResolver: RevisionResolver,
    private val matchContextFactory: AvlMatchContextFactory,
) {
    fun derive(
        feedCode: String,
        startDate: LocalDate,
        endDate: LocalDate,
        routeId: String? = null,
        tripId: String? = null,
    ): List<StopPassage> {
        val feed = avlFeeds.findByCode(feedCode) ?: throw IllegalArgumentException("no avl feed '$feedCode'")
        val zone = matchContextFactory.open(feed)?.zone ?: ZoneId.systemDefault()
        val revisionId = revisionResolver.resolve(feed.gtfsFeedCode, null)

        val from: Instant = startDate.atStartOfDay(zone).toInstant()
        val until: Instant = endDate.plusDays(1).atStartOfDay(zone).toInstant()

        val matches: List<VehicleMatch> =
            when {
                tripId != null -> {
                    val trip =
                        trips.findByTripId(revisionId, tripId)
                            ?: throw IllegalArgumentException("no trip '$tripId' in revision $revisionId")
                    vehicleMatches.findForTripsInWindow(feedCode, setOf(trip.id!!), from, until)
                }

                routeId != null -> {
                    val routeTrips = trips.findByRouteId(revisionId, routeId)
                    if (routeTrips.isEmpty()) {
                        throw IllegalArgumentException("no route '$routeId' in revision $revisionId")
                    }
                    vehicleMatches.findForTripsInWindow(
                        feedCode,
                        routeTrips.map { it.id!! },
                        from,
                        until,
                    )
                }

                else -> {
                    vehicleMatches.findInWindow(feedCode, from, until)
                }
            }
        if (matches.isEmpty()) return emptyList()

        // Chunked to stay well under Postgres's ~65,535 bind-parameter limit: at realistic AVL
        // polling volume (and the 14-day retention window this feature relies on), an unscoped or
        // multi-day route-wide query can easily produce well over 65,535 distinct avlReportIds.
        val reportsById =
            matches
                .map { it.avlReportId }
                .distinct()
                .chunked(20_000)
                .flatMap { avlReports.findAllById(it) }
                .associateBy { it.id }

        val tripCache = mutableMapOf<Long, Trip?>()

        fun tripFor(tripRowId: Long): Trip? = tripCache.getOrPut(tripRowId) { trips.findById(tripRowId).orElse(null) }

        data class ResolvedMatch(
            val match: VehicleMatch,
            val trip: Trip,
            val serviceDate: LocalDate,
        )

        val resolved =
            matches.mapNotNull { match ->
                val report = reportsById[match.avlReportId] ?: return@mapNotNull null
                val trip = tripFor(match.tripRowId) ?: return@mapNotNull null
                val serviceDate = inferServiceDate(report.toView(), trip.toRef(), zone)
                ResolvedMatch(match, trip, serviceDate)
            }

        val scheduleCache = mutableMapOf<Long, List<ScheduleTime>>()
        val stopPathCache = mutableMapOf<Long, List<StopPath>>()
        val stopNameCache = mutableMapOf<Long, Map<String, String?>>()

        return resolved
            .groupBy { Triple(it.trip.id!!, it.serviceDate, it.match.vehicleId) }
            .flatMap { (key, group) ->
                val (tripRowId, serviceDate, vehicleId) = key
                val trip = group.first().trip
                val tripPatternId = trip.tripPatternId ?: return@flatMap emptyList()

                // Use this group's own trip's revision for the schedule/stop-path lookups, not the
                // feed's globally-resolved *current* revision: a historical match within the AVL
                // retention window may belong to a trip from an older revision (if the active
                // revision has since changed), and looking it up under the current revision would
                // silently return zero rows instead of the real schedule/pattern shape it was
                // actually matched under.
                val tripRevisionId = trip.revisionId
                val schedule =
                    scheduleCache.getOrPut(tripRowId) { scheduleTimes.findByTripOrdered(tripRevisionId, tripRowId) }
                val paths =
                    stopPathCache.getOrPut(tripPatternId) {
                        stopPaths.findByTripPatternOrdered(tripRevisionId, tripPatternId)
                    }
                val stopIdByIndex = paths.associate { it.stopPathIndex to it.stopId }
                val stopNameByStopId =
                    stopNameCache.getOrPut(tripPatternId) {
                        stops
                            .findByStopIds(tripRevisionId, paths.map { it.stopId }.distinct())
                            .associate { it.stopId to it.stopName }
                    }

                val sortedMatches = group.map { it.match }.sortedBy { it.ts }
                // A VehicleMatch's stopPathIndex is the SpatialMatcher-reported segment the vehicle
                // was traveling on at ping time, not the stop it had arrived at: because
                // PatternGeometry.endOfPath(i) = stopPathCumM[i+1], stop k physically sits at
                // cumulative distance cum[k+1], so a ping that reports stopPathIndex == j means the
                // vehicle is traveling the segment ending at stop j - i.e. it has *already arrived*
                // at stop (j-1), and has *not yet* arrived at stop j. The first ping that confirms
                // arrival at stop k is therefore the first (by ts) match whose reported
                // stopPathIndex is STRICTLY GREATER than k, not `>=` - using `>=` latches onto the
                // ping that merely approaches stop k (still en route) as if it were the arrival,
                // attributing every passage one stop early.
                //
                // The earliest-by-ts match's reported stopPathIndex (`m`) tells us the vehicle had
                // already arrived at stop (m - 1) by the time AVL coverage for this execution began
                // (per the rule above), with no bound at all on how long before that arrival
                // actually happened - so stop (m - 1) is the most-recent stop this coverage can
                // meaningfully attest to (approximated by this very first ping, same convention used
                // for every other stop passage), while every stop strictly before it (index < m - 1)
                // has no data point bounding it whatsoever and must stay null rather than inherit
                // this first match (e.g. AVL starting late, already approaching stopPathIndex 10 -
                // meaning stop 9 was just reached and stops 0-8 must stay null).
                val minObservedStopPathIndex = sortedMatches.first().stopPathIndex

                // SpatialMatcher.stopPathIndexAt clamps its result to the pattern's last stop-path
                // index (paths.size - 1): no AVL match for an n-path pattern can ever report
                // stopPathIndex == n, so "one past the last stop" can never be observed as an index.
                // For every OTHER stop the strict `>` rule above is correct (see the comment above),
                // but applied unchanged to the pattern's FINAL scheduled stop it can never be
                // satisfied by any real data, making the terminal stop always come back unreached.
                // Special-case it: accept a match AT the last stop-path index whose distance along
                // the trip is within TERMINAL_STOP_TOLERANCE_M of the pattern's total shape length -
                // i.e. the vehicle has essentially reached the end of the route - in addition to (for
                // robustness, though currently unreachable given the clamp) any match reporting an
                // index strictly past it.
                val lastStopPathIndex = paths.maxOfOrNull { it.stopPathIndex }
                val totalLengthM = paths.sumOf { it.lengthM }

                schedule.map { scheduleTime ->
                    val isTerminalStop = lastStopPathIndex != null && scheduleTime.stopPathIndex == lastStopPathIndex
                    val observedMatch =
                        if (scheduleTime.stopPathIndex < minObservedStopPathIndex - 1) {
                            null
                        } else if (isTerminalStop) {
                            sortedMatches.firstOrNull {
                                it.stopPathIndex > scheduleTime.stopPathIndex ||
                                    (
                                        it.stopPathIndex == scheduleTime.stopPathIndex &&
                                            totalLengthM - it.distanceAlongTripM <= TERMINAL_STOP_TOLERANCE_M
                                    )
                            }
                        } else {
                            sortedMatches.firstOrNull { it.stopPathIndex > scheduleTime.stopPathIndex }
                        }
                    val stopId = stopIdByIndex[scheduleTime.stopPathIndex] ?: ""
                    StopPassage(
                        tripId = trip.tripId,
                        routeId = trip.routeId,
                        blockId = trip.blockId,
                        serviceDate = serviceDate,
                        stopId = stopId,
                        stopName = stopNameByStopId[stopId],
                        stopPathIndex = scheduleTime.stopPathIndex,
                        scheduledSec = scheduleTime.arrivalSec ?: scheduleTime.departureSec,
                        observedSec = observedMatch?.let { TemporalMatcher().toServiceSeconds(it.ts, zone, serviceDate) },
                        adherenceSec = observedMatch?.scheduleAdherenceSec,
                        vehicleId = vehicleId,
                        direction = trip.directionId,
                        occupancyStatus =
                            observedMatch?.let { reportsById[it.avlReportId]?.occupancyStatus }?.let {
                                runCatching { AvlOccupancyStatus.entries[it] }.getOrNull()
                            },
                    )
                }
            }
            // Groups are keyed by *inferred* serviceDate, which for a cross-midnight trip can fall
            // just outside the requested range even though its matches were fetched inside the
            // ts-based AVL window above - filter the final result back down to the requested
            // calendar-date range so such a group never leaks into an adjacent day's query.
            .filter { it.serviceDate >= startDate && it.serviceDate <= endDate }
    }
}

private fun AvlReportRow.toView() =
    AvlReportView(
        id = id!!,
        feedCode = feedCode,
        vehicleId = vehicleId,
        vehicleLabel = vehicleLabel,
        ts = ts,
        lat = lat,
        lon = lon,
        bearing = bearing,
        speedMps = speedMps,
        odometerM = odometerM,
        descTripId = descTripId,
        descRouteId = descRouteId,
        descDirectionId = descDirectionId,
        descStartDate = descStartDate,
        descStartTimeSec = descStartTimeSec,
        descScheduleRelationship = descScheduleRelationship,
        currentStopSequence = currentStopSequence,
        currentStopId = currentStopId,
        currentStatus = currentStatus,
        occupancyStatus = occupancyStatus,
        congestionLevel = congestionLevel,
    )

private fun Trip.toRef() =
    TripRef(
        id = id!!,
        revisionId = revisionId,
        tripId = tripId,
        routeId = routeId,
        serviceId = serviceId,
        tripHeadsign = tripHeadsign,
        tripShortName = tripShortName,
        directionId = directionId,
        blockId = blockId,
        shapeId = shapeId,
        wheelchairAccessible = wheelchairAccessible,
        bikesAllowed = bikesAllowed,
        tripPatternId = tripPatternId,
        startTimeSec = startTimeSec,
        endTimeSec = endTimeSec,
        frequencyBased = frequencyBased,
        noSchedule = noSchedule,
    )
