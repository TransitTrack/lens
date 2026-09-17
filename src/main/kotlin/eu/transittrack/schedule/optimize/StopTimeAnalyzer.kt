package eu.transittrack.schedule.optimize

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationKind
import eu.transittrack.schedule.optimize.model.OptimizationRecommendationRow

/**
 * Design 6.1: stop-time adjustments, one per (trip pattern, stop-path segment) partition. Runs
 * before [TripShiftAnalyzer] (see [getOrder]) so trip-shift can skip trips this analyzer already
 * claims — see `docs/superpowers/specs/2026-09-17-optimization-analyzer-architecture-design.md`.
 */
@Component
class StopTimeAnalyzer(
    private val properties: OptimizationProperties,
    private val json: JsonMapper,
) : OptimizationAnalyzer {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "stop-time"

    override fun getOrder(): Int = 100

    private data class AffectedTrip(
        val trip: Trip,
        val schedule: List<ScheduleTime>,
        val segmentPos: Int,
    )

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

        val materiality = properties.materialitySec
        val tripsByPattern = context.eligibleTrips.groupBy { it.tripPatternId!! }
        val results = mutableListOf<OptimizationRecommendationRow>()

        var noPatternMatch = 0
        var noAffectedTrips = 0
        var noScheduledMedian = 0
        var rejectedByEngine = 0
        var rejectedNegativeDelta = 0
        var rejectedNoTargets = 0

        val byPatternAndIndex = context.crossings.groupBy { it.tripPatternId to it.stopPathIndex }
        log.debug("run {}: [{}] {} (pattern, stopPathIndex) partitions to evaluate", run.id, name, byPatternAndIndex.size)
        for ((key, samples) in byPatternAndIndex) {
            val (patternId, stopPathIndex) = key
            val patternTrips = tripsByPattern[patternId]
            if (patternTrips == null) {
                noPatternMatch++
                continue
            }

            val affected =
                patternTrips.mapNotNull { t ->
                    val schedule = context.scheduleFor(t.id!!)
                    val pos = schedule.indexOfFirst { it.stopPathIndex == stopPathIndex }
                    if (pos < 0) return@mapNotNull null
                    if (schedule[pos].arrivalSec == null) return@mapNotNull null
                    AffectedTrip(t, schedule, pos)
                }
            if (affected.isEmpty()) {
                noAffectedTrips++
                continue
            }

            val scheduledSec = medianInt(affected.mapNotNull { it.schedule[it.segmentPos].schedTravelTimeSec })
            if (scheduledSec == null) {
                noScheduledMedian++
                continue
            }

            val candidate =
                RecommendationEngine.stopTime(
                    scheduledSec = scheduledSec,
                    observedSec = samples.map { it.observedTravelTimeSec },
                    minimumSamples = run.minimumSamples,
                    materialitySec = materiality,
                )
            if (candidate == null) {
                rejectedByEngine++
                continue
            }

            val delta = candidate.deltaSec
            // Design 6.1's nondecreasing-timetable gate: this segment's delta shifts every downstream
            // arrival/departure in the affected trips by the same amount, so a negative delta would
            // always move published times earlier than what riders already see today — always
            // discard rather than only checking a single boundary crossing.
            if (delta < 0) {
                rejectedNegativeDelta++
                continue
            }
            val stopSeqByIndex = context.stopSeqByIndex(patternId)

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
            if (perTrip.isEmpty()) {
                rejectedNoTargets++
                continue
            }

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
                ).apply { putAll(context.predictionEvidence(affectedTripRowIds.toList(), stopPathIndex)) }

            results +=
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
        }
        log.debug(
            "run {}: [{}] {} generated, {} dropped (no pattern match={}, no affected trips={}, " +
                "no scheduled median={}, rejected by engine thresholds={}, negative delta={}, no targets left={})",
            run.id, name, results.size,
            noPatternMatch + noAffectedTrips + noScheduledMedian + rejectedByEngine + rejectedNegativeDelta + rejectedNoTargets,
            noPatternMatch, noAffectedTrips, noScheduledMedian, rejectedByEngine, rejectedNegativeDelta, rejectedNoTargets,
        )
        return results
    }

    private fun medianInt(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
