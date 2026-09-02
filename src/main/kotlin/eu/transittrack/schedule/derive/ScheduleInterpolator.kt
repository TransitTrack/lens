package eu.transittrack.schedule.derive

import kotlin.math.roundToInt

data class RawStopTime(
    val stopPathIndex: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
)

data class ResolvedScheduleTime(
    val stopPathIndex: Int,
    val arrivalSec: Int,
    val departureSec: Int,
    val interpolated: Boolean,
    val schedTravelTimeSec: Int?,
    val schedDwellTimeSec: Int,
)

/**
 * Turns the partial arrival/departure times GTFS provides (only some stops are timed; first and
 * last always are) into a dense per-stop schedule. Missing times are linearly interpolated along
 * cumulative distance between the nearest enclosing timed stops.
 */
object ScheduleInterpolator {
    fun resolve(
        raw: List<RawStopTime>,
        cumulativeDistM: DoubleArray,
    ): List<ResolvedScheduleTime> {
        require(raw.size == cumulativeDistM.size) { "raw and distance sizes differ" }
        require(raw.isNotEmpty()) { "raw must not be empty" }

        // A single "anchor" time per stop: prefer arrival, else departure. Null when neither.
        val anchor = IntArray(raw.size)
        val known = BooleanArray(raw.size)
        raw.forEachIndexed { i, r ->
            val a = r.arrivalSec ?: r.departureSec
            if (a != null) {
                anchor[i] = a
                known[i] = true
            }
        }
        require(known.first() && known.last()) { "first and last stop must have a time" }

        // Fill unknown anchors by interpolation between surrounding known indices.
        var i = 0
        while (i < raw.size) {
            if (known[i]) {
                i++
                continue
            }
            val lo = i - 1
            var hi = i
            while (!known[hi]) hi++
            val span = cumulativeDistM[hi] - cumulativeDistM[lo]
            val timeSpan = anchor[hi] - anchor[lo]
            for (k in i until hi) {
                val frac =
                    if (span > 0.0) {
                        (cumulativeDistM[k] - cumulativeDistM[lo]) / span
                    } else {
                        (k - lo).toDouble() / (hi - lo)
                    }
                anchor[k] = anchor[lo] + (timeSpan * frac).roundToInt()
                known[k] = true
            }
            i = hi + 1
        }

        // Build resolved rows: keep real arr/dep where given, else use the anchor for both.
        val out = ArrayList<ResolvedScheduleTime>(raw.size)
        var prevDeparture: Int? = null
        raw.forEachIndexed { idx, r ->
            val wasInterpolated = r.arrivalSec == null && r.departureSec == null
            val arr = r.arrivalSec ?: anchor[idx]
            val dep = r.departureSec ?: anchor[idx]
            val travel = prevDeparture?.let { arr - it }
            out.add(
                ResolvedScheduleTime(
                    stopPathIndex = r.stopPathIndex,
                    arrivalSec = arr,
                    departureSec = dep,
                    interpolated = wasInterpolated,
                    schedTravelTimeSec = travel,
                    schedDwellTimeSec = dep - arr,
                ),
            )
            prevDeparture = dep
        }
        return out
    }
}
