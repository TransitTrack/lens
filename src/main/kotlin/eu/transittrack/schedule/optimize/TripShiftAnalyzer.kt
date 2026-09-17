package eu.transittrack.schedule.optimize

import kotlin.math.abs
import kotlin.math.roundToInt

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow

/**
 * Design 6.2: trip shift / headway adjustments, one per eligible trip ordered by scheduled first
 * departure. Runs after [StopTimeAnalyzer] (see [getOrder]) and skips any trip a stop-time
 * recommendation already targets, read off [OptimizationAnalysisContext.recommendationsSoFar]
 * rather than a parameter threaded by the pipeline — see
 * `docs/superpowers/specs/2026-09-17-optimization-analyzer-architecture-design.md`.
 *
 * Design decision recorded here because the original design spec/brief do not fully resolve it:
 * `avl_stop_crossing` never has a `stop_path_index = 0` row (crossings are only emitted for the
 * *destination* index of a detected inter-stop segment, see `eu.transittrack.predict.learn.Crossing`),
 * and the table carries no service-day/timezone metadata to convert its absolute `observed_at`
 * timestamp back into a GTFS seconds-of-service-day figure comparable to `Trip.startTimeSec`.
 * "Observed first-stop adherence" is therefore approximated per trip as: take the smallest
 * stop-path index actually crossed for that trip, and add the deviation between its observed and
 * scheduled segment-travel-time to the trip's scheduled start second.
 */
@Component
class TripShiftAnalyzer(
    private val properties: OptimizationProperties,
    private val json: JsonMapper,
) : OptimizationAnalyzer {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "trip-shift"

    override fun getOrder(): Int = 200

    override fun analyze(context: OptimizationAnalysisContext): List<OptimizationRecommendationRow> {
        val run = context.run
        if (context.avlFeedIds.isEmpty()) {
            log.info("run {}: [{}] no AVL feed mapped to gtfs feed {}; 0 recommendations", run.id, name, run.feedId)
            return emptyList()
        }
        if (context.crossings.isEmpty()) {
            log.info(
                "run {}: [{}] no AVL crossings for eligible trips in observed window {}..{}; 0 recommendations",
                run.id, name, run.observedFrom, run.observedTo,
            )
            return emptyList()
        }

        val excludedTripRowIds = stopTimeAffectedTripRowIds(context)
        val materiality = properties.materialitySec
        val ordered = context.eligibleTrips.filter { it.startTimeSec != null }.sortedBy { it.startTimeSec!! }
        if (ordered.size < 2) {
            log.debug("run {}: [{}] only {} trip(s) with a scheduled start time; needs at least 2", run.id, name, ordered.size)
            return emptyList()
        }

        val crossingsByTrip = context.crossings.groupBy { it.tripRowId }
        val results = mutableListOf<OptimizationRecommendationRow>()

        var excludedByStopTime = 0
        var noNeighbor = 0
        var noCrossings = 0
        var noScheduledSegment = 0
        var noObservedSamples = 0
        var rejectedByEngine = 0

        for (idx in ordered.indices) {
            val trip = ordered[idx]
            val tripRowId = trip.id!!
            // A trip-shift candidate must not overlap a stop-time recommendation's same-trip target.
            if (tripRowId in excludedTripRowIds) {
                excludedByStopTime++
                continue
            }
            val scheduledStart = trip.startTimeSec ?: continue
            val predecessor = if (idx > 0) ordered[idx - 1] else null
            val successor = if (idx < ordered.size - 1) ordered[idx + 1] else null
            val predecessorStart = predecessor?.startTimeSec
            val successorStart = successor?.startTimeSec
            if (predecessorStart == null && successorStart == null) {
                noNeighbor++
                continue
            }

            val tripCrossings = crossingsByTrip[tripRowId]
            if (tripCrossings == null) {
                noCrossings++
                continue
            }
            val firstIndex = tripCrossings.minOfOrNull { it.stopPathIndex } ?: continue
            val schedule = context.scheduleFor(tripRowId)
            val schedSeg = schedule.firstOrNull { it.stopPathIndex == firstIndex }?.schedTravelTimeSec
            if (schedSeg == null) {
                noScheduledSegment++
                continue
            }

            // Approximate observed start adherence from the deviation at the earliest crossed
            // segment; see the class doc for why (no stop_path_index=0 rows, no stored timezone).
            val observedStartSamples =
                tripCrossings
                    .filter { it.stopPathIndex == firstIndex }
                    .map { scheduledStart + (it.observedTravelTimeSec - schedSeg) }
            if (observedStartSamples.isEmpty()) {
                noObservedSamples++
                continue
            }

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
                )
            if (candidate == null) {
                rejectedByEngine++
                continue
            }

            val evidence =
                linkedMapOf<String, Any?>(
                    "sampleCount" to candidate.sampleCount,
                    "medianObservedStartSec" to candidate.observedStartSec,
                    "scheduledStartSec" to scheduledStart,
                    "dispersionMad" to RobustStatistics.filter(observedStartSamples).mad,
                    "targetHeadwaySec" to targetHeadway,
                ).apply { putAll(context.predictionEvidence(listOf(tripRowId), firstIndex)) }

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
        log.debug(
            "run {}: [{}] {} generated, {} dropped (excluded by stop-time={}, no neighbor trip={}, " +
                "no crossings={}, no scheduled segment={}, no observed samples={}, rejected by engine thresholds={})",
            run.id, name, results.size,
            excludedByStopTime + noNeighbor + noCrossings + noScheduledSegment + noObservedSamples + rejectedByEngine,
            excludedByStopTime, noNeighbor, noCrossings, noScheduledSegment, noObservedSamples, rejectedByEngine,
        )
        return results
    }

    private fun stopTimeAffectedTripRowIds(context: OptimizationAnalysisContext): Set<Long> {
        // GTFS trip_id is unique within a single revision (eligibleTrips' scope), so this associate is lossless.
        val tripRowIdByTripId = context.eligibleTrips.associate { it.tripId to it.id!! }
        val tripIds = mutableSetOf<String>()
        context.recommendationsSoFar
            .filter { it.kind == OptimizationRecommendationKind.STOP_TIME }
            .forEach { row ->
                val proposed = row.proposedValue ?: return@forEach
                json.readTree(proposed).path("targets").forEach { t -> tripIds += t.path("tripId").asString() }
            }
        return tripIds.mapNotNull { tripRowIdByTripId[it] }.toSet()
    }
}
