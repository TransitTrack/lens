package eu.transittrack.schedule.derive

import org.slf4j.LoggerFactory

import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.model.Frequency
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.ScheduleProperties
import eu.transittrack.schedule.model.SchedTrip
import eu.transittrack.schedule.model.ScheduleTime

/**
 * Stage 2 of schedule derivation. Per route, per trip: interpolate the cleaned stop_times into a
 * dense schedule, 0-base frequency trips, build `sched_trip` + `schedule_time`, link the raw
 * `trips.trip_pattern_id`, and append a [DerivedTrip] to the shared context for later stages.
 *
 * A trip whose stop_times carry no arrival/departure at all is "no-schedule": rejected (interpolation
 * throws) unless [ScheduleProperties.tolerateNoScheduleTrips], in which case it becomes a
 * `sched_trip` with `no_schedule = true`, a 0..86400 span and null `schedule_time` arr/dep.
 */
class SchedTripProcessor(
    private val context: DerivationContext,
    private val writer: ScheduleWriter,
    private val derivedGtfsWriter: DerivedGtfsWriter,
    private val props: ScheduleProperties,
    private val routes: RouteRepository,
    private val trips: TripRepository,
    private val frequencies: FrequencyRepository,
) : IngestionPostProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ORDER = 20
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        val state = context.get(revisionId)

        // Spec: "the trip's first gtfs_frequency row" — keep the first, not the last.
        val freqByTrip: Map<String, Frequency> =
            frequencies
                .findByRevisionId(revisionId)
                .groupBy { it.tripId }
                .mapValues { it.value.first() }

        var schedTripCount = 0L
        var scheduleTimeCount = 0L
        val tripPatternLinks = HashMap<Long, Long>() // trips.id -> trip_patterns.id

        for (route in routes.findByRevisionId(revisionId)) {
            val routeTrips = trips.findByRouteId(revisionId, route.routeId)
            if (routeTrips.isEmpty()) continue

            val newTrips = ArrayList<SchedTrip>()
            val pendingTimes = ArrayList<PendingTimes>() // parallel to newTrips
            val derivedBase = state.derivedTrips.size

            for (trip in routeTrips) {
                val cleaned = state.cleanedRows[trip.tripId] ?: continue
                if (cleaned.size < 2) continue
                val stopIds = cleaned.map { it.stopId }
                val patternId =
                    state.patternIdByKey[PatternKey.of(trip.routeId, trip.shapeId, stopIds)] ?: continue
                val cum = state.patternCumDist.getValue(patternId)
                val raw = cleaned.mapIndexed { i, r -> RawStopTime(i, r.arrivalSec, r.departureSec) }
                val freq = freqByTrip[trip.tripId]
                val timeless = cleaned.all { it.arrivalSec == null && it.departureSec == null }

                val resolved: List<ResolvedScheduleTime>
                var noSchedule = false
                if (timeless && freq == null) {
                    if (!props.tolerateNoScheduleTrips) {
                        // Throws IllegalStateException("... first and last stop must have a time").
                        withTripDerivationContext(trip) { ScheduleInterpolator.resolve(raw, cum) }
                    }
                    noSchedule = true
                    resolved =
                        cleaned.indices.map {
                            ResolvedScheduleTime(
                                it, 0, 0,
                                interpolated = false,
                                schedTravelTimeSec = null,
                                schedDwellTimeSec = 0,
                            )
                        }
                    log.warn("trip {} (route {}): no schedule times; tolerated as no_schedule", trip.tripId, trip.routeId)
                } else {
                    var r = withTripDerivationContext(trip) { ScheduleInterpolator.resolve(raw, cum) }
                    if (freq != null) {
                        val base = r.first().departureSec
                        r = r.map { it.copy(arrivalSec = it.arrivalSec - base, departureSec = it.departureSec - base) }
                    }
                    resolved = r
                }

                val st =
                    SchedTrip(
                        revisionId = revisionId,
                        tripPatternId = patternId,
                        tripId = trip.tripId,
                        routeId = trip.routeId,
                        serviceId = trip.serviceId,
                        directionId = trip.directionId,
                        headsign = Headsigns.resolve(trip.tripHeadsign, cleaned.first().stopHeadsign),
                        tripShortName = trip.tripShortName,
                        startTimeSec = if (noSchedule) 0 else resolved.first().departureSec,
                        endTimeSec = if (noSchedule) 86_400 else resolved.last().arrivalSec,
                        frequencyBased = freq != null,
                        exactTimes = freq?.exactTimes,
                        noSchedule = noSchedule,
                    )
                newTrips.add(st)
                pendingTimes.add(PendingTimes(resolved, noSchedule))
                state.derivedTrips.add(
                    DerivedTrip(
                        schedTripId = -1,
                        tripId = trip.tripId,
                        patternId = patternId,
                        blockId = trip.blockId?.ifBlank { null },
                        serviceId = trip.serviceId,
                        routeId = trip.routeId,
                        startSec = st.startTimeSec,
                        endSec = st.endTimeSec,
                        firstStopId = stopIds.first(),
                        lastStopId = stopIds.last(),
                        frequencyBased = freq != null,
                        noSchedule = noSchedule,
                        resolved = resolved,
                    ),
                )
                tripPatternLinks[trip.id!!] = patternId
            }

            writer.write(newTrips) // assigns sched_trip ids
            schedTripCount += newTrips.size
            newTrips.forEachIndexed { i, st -> state.derivedTrips[derivedBase + i].schedTripId = st.id!! }

            val times = ArrayList<ScheduleTime>()
            newTrips.forEachIndexed { i, st ->
                val p = pendingTimes[i]
                for (r in p.resolved) {
                    times.add(
                        ScheduleTime(
                            revisionId = revisionId,
                            schedTripId = st.id!!,
                            stopPathIndex = r.stopPathIndex,
                            arrivalSec = if (p.noSchedule) null else r.arrivalSec,
                            departureSec = if (p.noSchedule) null else r.departureSec,
                            interpolated = r.interpolated,
                            schedTravelTimeSec = r.schedTravelTimeSec,
                            schedDwellTimeSec = r.schedDwellTimeSec,
                        ),
                    )
                }
            }
            writer.write(times)
            scheduleTimeCount += times.size
        }

        derivedGtfsWriter.applyTripPatternLinks(tripPatternLinks)
        return mapOf("sched_trip" to schedTripCount, "schedule_time" to scheduleTimeCount)
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { writer.deleteTables(revisionId, "schedule_time", "sched_trip") }
        runCatching { derivedGtfsWriter.clearTripPatternLinks(revisionId) }
        runCatching { context.close(revisionId) }
    }

    private class PendingTimes(
        val resolved: List<ResolvedScheduleTime>,
        val noSchedule: Boolean,
    )
}

/**
 * Runs one trip's derivation, rethrowing any failure with the trip and route named — otherwise a
 * single malformed trip fails the whole revision with a message ("first and last stop must have a
 * time") that identifies nothing.
 */
private inline fun <T> withTripDerivationContext(
    trip: Trip,
    body: () -> T,
): T =
    try {
        body()
    } catch (e: Exception) {
        throw IllegalStateException("trip ${trip.tripId} (route ${trip.routeId}): ${e.message}", e)
    }
