package eu.transittrack.avl.match

import java.sql.Timestamp
import java.time.Instant

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties

/**
 * Ages out matched vehicles that have stopped sending reports.
 *
 * [AvlMatchProcessor] only re-evaluates a vehicle when a fresh `avl_report` arrives, so its
 * `consecutive_failures` counter never advances for a vehicle that simply goes silent — the
 * `vehicle_state` keeps `matched = true` and its trip/block assignment until age-based retention
 * hard-deletes the row hours later. This sweep closes that gap in two phases, keyed on
 * `vehicle_state.updated_at` (wall-clock, set only when a real report outcome is persisted, and
 * deliberately not touched here):
 *
 * - **stale** — silent for `feed.poll_interval_sec * silentStaleCycles` (floored at
 *   `silentUnmatchMinSec`): flag `stale`, keep the assignment.
 * - **unmatch** — silent for `... * silentUnmatchCycles`: drop the assignment and set
 *   `matched = false`, identical to the `unmatchAfterFailures` clear in [AvlMatchProcessor].
 */
@Component
@ConditionalOnProperty("transittrack.avl.enabled", havingValue = "true")
class AvlSilentVehicleSweeper(
    private val jdbc: JdbcTemplate,
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
            log.info("avl silent sweep: {} vehicles flagged stale, {} unmatched", c.staled, c.unmatched)
        }
    }

    fun sweep(now: Instant): SweepCounts {
        val ts = Timestamp.from(now)

        // Unmatch first: a vehicle past this threshold skips the stale pass below (matched -> false).
        val unmatched = jdbc.update(
            """
            update vehicle_state vs set
              matched = false, stale = true,
              revision_id = null, trip_row_id = null, block_pk = null, trip_pattern_id = null,
              stop_path_index = null, distance_along_trip_m = null, schedule_adherence_sec = null,
              snapped_lat = null, snapped_lon = null
            from avl_feed f
            where vs.feed_id = f.id
              and vs.trip_row_id is not null
              and vs.updated_at + (greatest(f.poll_interval_sec * ?::int, ?::int) * interval '1 second') < ?::timestamptz
            """.trimIndent(),
            cfg.silentUnmatchCycles, cfg.silentUnmatchMinSec, ts,
        )

        val staled = jdbc.update(
            """
            update vehicle_state vs set stale = true
            from avl_feed f
            where vs.feed_id = f.id
              and vs.matched = true and vs.stale = false
              and vs.updated_at + (greatest(f.poll_interval_sec * ?::int, ?::int) * interval '1 second') < ?::timestamptz
            """.trimIndent(),
            cfg.silentStaleCycles, cfg.silentUnmatchMinSec, ts,
        )

        return SweepCounts(staled = staled, unmatched = unmatched)
    }
}
