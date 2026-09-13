package eu.transittrack.avl.match

import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties
import eu.transittrack.avl.model.VehicleStateRepository

/**
 * Ages out matched vehicles that have stopped sending reports.
 *
 * [AvlMatchProcessor] only re-evaluates a vehicle when a fresh `avl_report` arrives, so its
 * `consecutive_failures` counter never advances for a vehicle that simply goes silent — the
 * `vehicle_state` keeps `matched = true` and its trip/block assignment until age-based retention
 * hard-deletes the row hours later. This sweep closes that gap in two phases, keyed on the newest
 * `avl_report.ts` per vehicle (a correlated `max(ts)` subquery in the two update queries below)
 * rather than `vehicle_state.updated_at`, so that a processing backlog — reports queued but not
 * yet claimed or matched — can never be mistaken for genuine vehicle silence:
 *
 * - **stale** — silent for `feed.poll_interval_sec * silentStaleCycles` (floored at
 *   `silentUnmatchMinSec`): flag `stale`, keep the assignment.
 * - **unmatch** — silent for `... * silentUnmatchCycles`: drop the assignment and set
 *   `matched = false`, identical to the `unmatchAfterFailures` clear in [AvlMatchProcessor].
 *
 * The two update queries themselves live on [VehicleStateRepository] (`unmatchSilent` /
 * `staleSilent`).
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlSilentVehicleSweeper(
    private val vehicleStates: VehicleStateRepository,
    props: AvlProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cfg = props.match

    data class SweepCounts(
        val staled: Int,
        val unmatched: Int,
    )

    @Scheduled(fixedDelayString = "\${transittrack.avl.match.match-interval-ms:5000}")
    fun sweep() {
        val c = sweep(Instant.now())
        if (c.staled > 0 || c.unmatched > 0) {
            log.info("avl silent sweep: ⚠️ {} vehicles flagged stale, ‼️ {} unmatched", c.staled, c.unmatched)
        }
    }

    fun sweep(now: Instant): SweepCounts {
        // Unmatch first: a vehicle past this threshold skips the stale pass below (matched -> false).
        val unmatched = vehicleStates.unmatchSilent(cfg.silentUnmatchCycles, cfg.silentUnmatchMinSec, now)
        val staled = vehicleStates.staleSilent(cfg.silentStaleCycles, cfg.silentUnmatchMinSec, now)
        return SweepCounts(staled = staled, unmatched = unmatched)
    }
}
