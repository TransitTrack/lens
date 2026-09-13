package eu.transittrack.schedule.optimize

import kotlin.math.abs
import kotlin.math.roundToInt

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.avl.model.AvlFeedRepository
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.predict.model.AvlStopCrossing
import eu.transittrack.predict.model.AvlStopCrossingRepository
import eu.transittrack.predict.model.PredictionAccuracyRepository
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow
import eu.transittrack.schedule.optimize.model.OptimizationRunRow

/**
 * Steps 2-6 of the analysis pipeline (design section 5, "Analysis pipeline"): loads the selected
 * schedule population and observed evidence from the run's *frozen* revision (never re-resolved),
 * trims outliers via [RecommendationEngine]/[RobustStatistics], and builds explainable
 * non-conflicting [OptimizationRecommendationRow]s (unpersisted). [ScheduleOptimizationService]
 * owns persistence and the QUEUED/RUNNING/SUCCEEDED/FAILED lifecycle (Task 3); this class is pure
 * query + computation so it stays independently testable.
 *
 * Design decision recorded here because the design spec/brief do not fully resolve it: `avl_stop_crossing`
 * never has a `stop_path_index = 0` row (crossings are only emitted for the *destination* index of
 * a detected inter-stop segment, see `eu.transittrack.predict.learn.Crossing`), and the table
 * carries no service-day/timezone metadata to convert its absolute `observed_at` timestamp back
 * into a GTFS seconds-of-service-day figure comparable to `Trip.startTimeSec`. Section 6.2's
 * "observed first-stop adherence" is therefore approximated per trip as: take the smallest
 * stop-path index actually crossed for that trip, and add the deviation between its observed and
 * scheduled segment-travel-time to the trip's scheduled start second. This reuses only fields
 * already on `avl_stop_crossing`/`schedule_time` and requires no timezone/service-date modeling.
 */
@Component
class OptimizationAnalysisPipeline(
    private val gtfsFeeds: GtfsFeedRepository,
    private val avlFeeds: AvlFeedRepository,
    private val trips: TripRepository,
    private val stopPaths: StopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val crossings: AvlStopCrossingRepository,
    private val predictionAccuracy: PredictionAccuracyRepository,
    private val properties: OptimizationProperties,
    private val json: JsonMapper,
) {
    fun analyze(run: OptimizationRunRow): List<OptimizationRecommendationRow> {
        val eligibleTrips =
            trips
                .findEligible(run.revisionId, run.serviceId, run.routeId, run.directionId, run.windowFromSec, run.windowToSec)
                .filter { it.tripPatternId != null && it.id != null }
        if (eligibleTrips.isEmpty()) return emptyList()

        // avl_stop_crossing/prediction_accuracy are keyed by avl_feed.id, not gtfs_feed.id (the run's
        // frozen feed reference) — translate via the avl_feed -> gtfs_feed_code link.
        val avlFeedIds = avlFeedIdsFor(run.feedId)
        if (avlFeedIds.isEmpty()) return emptyList()

        val eligibleTripRowIds = eligibleTrips.mapNotNull { it.id }.toSet()
        val crossingRows =
            crossings
                .findForAnalysis(
                    avlFeedIds,
                    run.revisionId,
                    run.observedFrom,
                    run.observedTo,
                    run.serviceId,
                    run.routeId,
                    run.directionId,
                    run.windowFromSec,
                    run.windowToSec,
                ).filter { it.tripRowId in eligibleTripRowIds }
        if (crossingRows.isEmpty()) return emptyList()

        val stopTimeResults = generateStopTimeCandidates(run, eligibleTrips, crossingRows, avlFeedIds)
        val stopTimeAffectedTripRowIds = stopTimeResults.flatMap { it.affectedTripRowIds }.toSet()
        val tripShiftRows = generateTripShiftCandidates(run, eligibleTrips, crossingRows, stopTimeAffectedTripRowIds, avlFeedIds)

        return stopTimeResults.map { it.row } + tripShiftRows
    }

    private fun avlFeedIdsFor(gtfsFeedId: Long): List<Long> {
        val code = gtfsFeeds.findById(gtfsFeedId).orElse(null)?.code ?: return emptyList()
        return avlFeeds.findByGtfsFeedCode(code).mapNotNull { it.id }
    }

    private data class StopTimeResult(
        val row: OptimizationRecommendationRow,
        val affectedTripRowIds: Set<Long>,
    )

    private data class AffectedTrip(
        val trip: Trip,
        val schedule: List<ScheduleTime>,
        val segmentPos: Int,
    )

    /** Design 6.1: stop-time adjustments, one per (trip pattern, stop-path segment) partition. */
    private fun generateStopTimeCandidates(
        run: OptimizationRunRow,
        eligibleTrips: List<Trip>,
        crossingRows: List<AvlStopCrossing>,
        avlFeedIds: List<Long>,
    ): List<StopTimeResult> {
        val materiality = properties.materialitySec
        val tripsByPattern = eligibleTrips.groupBy { it.tripPatternId!! }
        val results = mutableListOf<StopTimeResult>()

        val byPatternAndIndex = crossingRows.groupBy { it.tripPatternId to it.stopPathIndex }
        for ((key, samples) in byPatternAndIndex) {
            val (patternId, stopPathIndex) = key
            val patternTrips = tripsByPattern[patternId] ?: continue

            val affected =
                patternTrips.mapNotNull { t ->
                    val schedule = scheduleTimes.findByTripOrdered(run.revisionId, t.id!!)
                    val pos = schedule.indexOfFirst { it.stopPathIndex == stopPathIndex }
                    if (pos < 0) return@mapNotNull null
                    if (schedule[pos].arrivalSec == null) return@mapNotNull null
                    AffectedTrip(t, schedule, pos)
                }
            if (affected.isEmpty()) continue

            val scheduledSec = medianInt(affected.mapNotNull { it.schedule[it.segmentPos].schedTravelTimeSec }) ?: continue

            val candidate =
                RecommendationEngine.stopTime(
                    scheduledSec = scheduledSec,
                    observedSec = samples.map { it.observedTravelTimeSec },
                    minimumSamples = run.minimumSamples,
                    materialitySec = materiality,
                ) ?: continue

            val delta = candidate.deltaSec
            // Design 6.1's nondecreasing-timetable gate: this segment's delta shifts every downstream
            // arrival/departure in the affected trips by the same amount, so a negative delta would
            // always move published times earlier than what riders already see today — always
            // discard rather than only checking a single boundary crossing.
            if (delta < 0) continue
            val stopSeqByIndex = stopPaths.findByTripPatternOrdered(run.revisionId, patternId).associate { it.stopPathIndex to it.stopSeq }

            val perTrip = mutableListOf<Triple<Trip, List<Map<String, Any?>>, List<Map<String, Any?>>>>()
            for (aff in affected) {
                val schedule = aff.schedule
                val pos = aff.segmentPos

                val current = mutableListOf<Map<String, Any?>>()
                val proposed = mutableListOf<Map<String, Any?>>()
                for (i in pos until schedule.size) {
                    val s = schedule[i]
                    // A cell the GTFS feed itself left blank (interpolated by the deriver, not
                    // explicitly declared) must never be silently pinned to a concrete published
                    // value — skip emitting a target for it entirely.
                    if (s.interpolated) continue
                    val seq = stopSeqByIndex[s.stopPathIndex] ?: s.stopPathIndex
                    current +=
                        linkedMapOf(
                            "tripId" to aff.trip.tripId,
                            "stopSequence" to seq,
                            "arrivalSec" to s.arrivalSec,
                            "departureSec" to s.departureSec,
                        )
                    proposed +=
                        linkedMapOf(
                            "tripId" to aff.trip.tripId,
                            "stopSequence" to seq,
                            "arrivalSec" to s.arrivalSec?.plus(delta),
                            "departureSec" to s.departureSec?.plus(delta),
                        )
                }
                // If every downstream cell for this trip was interpolated, this trip contributes no
                // target at all — it must not appear in perTrip (not a broken/empty op).
                if (current.isEmpty()) continue
                perTrip += Triple(aff.trip, current, proposed)
            }
            if (perTrip.isEmpty()) continue

            val sorted = perTrip.sortedBy { it.first.tripId }
            val repTrip = sorted.first().first
            val repSeq = stopSeqByIndex[stopPathIndex] ?: stopPathIndex
            val affectedTripRowIds = sorted.map { it.first.id!! }.toSet()

            val evidence =
                linkedMapOf<String, Any?>(
                    "sampleCount" to candidate.sampleCount,
                    "medianObservedTravelTimeSec" to candidate.observedSec,
                    "scheduledTravelTimeSec" to scheduledSec,
                    "dispersionMad" to RobustStatistics.filter(samples.map { it.observedTravelTimeSec }).mad,
                ).apply { putAll(predictionEvidence(run, avlFeedIds, affectedTripRowIds.toList(), stopPathIndex)) }

            val row =
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.STOP_TIME,
                    sampleCount = candidate.sampleCount,
                    deltaSec = delta,
                    reason =
                        "Observed travel time to stop sequence $repSeq (trip ${repTrip.tripId}) differs from schedule " +
                            "by ${delta}s across ${candidate.sampleCount} samples.",
                    currentValue = json.writeValueAsString(linkedMapOf("targets" to sorted.flatMap { it.second })),
                    proposedValue = json.writeValueAsString(linkedMapOf("targets" to sorted.flatMap { it.third })),
                    evidence = json.writeValueAsString(evidence),
                    conflictKey = "cell:${repTrip.tripId}:$repSeq",
                )
            results += StopTimeResult(row, affectedTripRowIds)
        }
        return results
    }

    /** Design 6.2: trip shift / headway adjustments, one per eligible trip ordered by scheduled first departure. */
    private fun generateTripShiftCandidates(
        run: OptimizationRunRow,
        eligibleTrips: List<Trip>,
        crossingRows: List<AvlStopCrossing>,
        excludedTripRowIds: Set<Long>,
        avlFeedIds: List<Long>,
    ): List<OptimizationRecommendationRow> {
        val materiality = properties.materialitySec
        val ordered = eligibleTrips.filter { it.startTimeSec != null }.sortedBy { it.startTimeSec!! }
        if (ordered.size < 2) return emptyList()

        val crossingsByTrip = crossingRows.groupBy { it.tripRowId }
        val results = mutableListOf<OptimizationRecommendationRow>()

        for (idx in ordered.indices) {
            val trip = ordered[idx]
            val tripRowId = trip.id!!
            // Design 6.2: a trip-shift candidate must not overlap a stop-time recommendation's
            // same-trip target.
            if (tripRowId in excludedTripRowIds) continue
            val scheduledStart = trip.startTimeSec ?: continue
            val predecessor = if (idx > 0) ordered[idx - 1] else null
            val successor = if (idx < ordered.size - 1) ordered[idx + 1] else null
            val predecessorStart = predecessor?.startTimeSec
            val successorStart = successor?.startTimeSec
            if (predecessorStart == null && successorStart == null) continue

            val tripCrossings = crossingsByTrip[tripRowId] ?: continue
            val firstIndex = tripCrossings.minOfOrNull { it.stopPathIndex } ?: continue
            val schedule = scheduleTimes.findByTripOrdered(run.revisionId, tripRowId)
            val schedSeg = schedule.firstOrNull { it.stopPathIndex == firstIndex }?.schedTravelTimeSec ?: continue

            // Approximate observed start adherence from the deviation at the earliest crossed
            // segment; see the class doc for why (no stop_path_index=0 rows, no stored timezone).
            val observedStartSamples =
                tripCrossings
                    .filter { it.stopPathIndex == firstIndex }
                    .map { scheduledStart + (it.observedTravelTimeSec - schedSeg) }
            if (observedStartSamples.isEmpty()) continue

            val targetHeadway =
                when {
                    predecessorStart != null && successorStart != null -> ((successorStart - predecessorStart) / 2.0).roundToInt()
                    predecessorStart != null -> scheduledStart - predecessorStart
                    successorStart != null -> successorStart - scheduledStart
                    else -> continue
                }

            val candidate =
                RecommendationEngine.tripShift(
                    tripId = trip.tripId,
                    scheduledStartSec = scheduledStart,
                    observedStartSec = observedStartSamples,
                    predecessorStartSec = predecessorStart,
                    successorStartSec = successorStart,
                    targetHeadwaySec = targetHeadway,
                    minimumSamples = run.minimumSamples,
                    materialitySec = materiality,
                ) ?: continue

            val evidence =
                linkedMapOf<String, Any?>(
                    "sampleCount" to candidate.sampleCount,
                    "medianObservedStartSec" to candidate.observedStartSec,
                    "scheduledStartSec" to scheduledStart,
                    "dispersionMad" to RobustStatistics.filter(observedStartSamples).mad,
                    "targetHeadwaySec" to targetHeadway,
                ).apply { putAll(predictionEvidence(run, avlFeedIds, listOf(tripRowId), firstIndex)) }

            results +=
                OptimizationRecommendationRow(
                    runId = run.id!!,
                    kind = OptimizationRecommendationKind.TRIP_SHIFT,
                    sampleCount = candidate.sampleCount,
                    deltaSec = candidate.deltaSec,
                    reason =
                        "Trip ${trip.tripId} is consistently observed ${abs(candidate.deltaSec)}s " +
                            "${if (candidate.deltaSec > 0) "later" else "earlier"} than scheduled, improving headway spacing.",
                    currentValue =
                        json.writeValueAsString(
                            linkedMapOf("targets" to listOf(linkedMapOf("tripId" to trip.tripId, "startTimeSec" to scheduledStart))),
                        ),
                    proposedValue =
                        json.writeValueAsString(
                            linkedMapOf(
                                "targets" to
                                    listOf(linkedMapOf("tripId" to trip.tripId, "startTimeSec" to candidate.observedStartSec)),
                            ),
                        ),
                    evidence = json.writeValueAsString(evidence),
                    conflictKey = "shift:${trip.tripId}",
                )
        }
        return results
    }

    /** Design 5 step 6: `prediction_accuracy` is context only, never an input to the timing proposal itself. */
    private fun predictionEvidence(
        run: OptimizationRunRow,
        avlFeedIds: List<Long>,
        tripRowIds: List<Long>,
        stopPathIndex: Int,
    ): Map<String, Double?> {
        val rows = predictionAccuracy.findForTargets(avlFeedIds, tripRowIds, stopPathIndex, run.observedFrom, run.observedTo)
        val meanError = if (rows.isEmpty()) null else rows.map { it.errorSec }.average()
        val meanAbsError = if (rows.isEmpty()) null else rows.map { it.absErrorSec }.average()
        return linkedMapOf("predictionMeanErrorSec" to meanError, "predictionMeanAbsErrorSec" to meanAbsError)
    }

    private fun medianInt(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
