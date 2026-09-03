package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.median
import eu.transittrack.schedule.model.TravelTimesForStopPath

/**
 * Stage 3 of schedule derivation. Per (trip pattern, stop path index): the median scheduled travel
 * and dwell time over that pattern's real (non-no-schedule) trips, written as one
 * `travel_times_for_stop_path` row per pair. Also sets `trip_patterns.trip_count`.
 *
 * Port of the former `ScheduleDerivationService.aggregatePass`, redirected from the dropped
 * `stop_path.typical_*` columns to `travel_times_for_stop_path`.
 */
class TravelTimesProcessor(
    private val context: DerivationContext,
    private val writer: ScheduleWriter,
) : IngestionPostProcessor {
    companion object {
        const val ORDER = 30
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        val state = context.get(revisionId)
        val byPattern = state.derivedTrips.groupBy { it.patternId }
        val rows = ArrayList<TravelTimesForStopPath>()
        for ((patternId, tripsOfPattern) in byPattern) {
            val pathIds = state.patternStopPathIds[patternId] ?: continue
            val real = tripsOfPattern.filter { !it.noSchedule }
            for (idx in pathIds.indices) {
                val travels = real.mapNotNull { it.resolved.getOrNull(idx)?.schedTravelTimeSec }
                val dwells = real.mapNotNull { it.resolved.getOrNull(idx)?.schedDwellTimeSec }
                rows.add(
                    TravelTimesForStopPath(
                        revisionId = revisionId,
                        tripPatternId = patternId,
                        stopPathIndex = idx,
                        travelTimeSec = median(travels),
                        dwellTimeSec = median(dwells),
                    ),
                )
            }
        }
        writer.write(rows)
        writer.applyTripPatternTripCount(byPattern.mapValues { it.value.size })
        return mapOf("travel_times_for_stop_path" to rows.size.toLong())
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { writer.deleteTables(revisionId, "travel_times_for_stop_path") }
        runCatching { writer.resetTripPatternTripCount(revisionId) }
        runCatching { context.close(revisionId) }
    }
}
