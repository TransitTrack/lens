package eu.transittrack.predict.learn

import java.time.Duration
import java.time.Instant

/**
 * A vehicle within this distance of its trip's start hasn't left the first stop yet: it's standing
 * at the terminus, even if its GPS projects a few metres onto the first real stop path.
 */
const val FIRST_STOP_RADIUS_M = 30.0

/**
 * A vehicle within this distance of its trip's end has reached the last stop: shapes often run on a
 * few metres past where vehicles actually stop, so its along-trip distance may never reach the
 * pattern's full length. The matcher's default `backtrackToleranceM` treats the same span as the
 * trip's end when it advances a vehicle to its next block trip.
 */
const val LAST_STOP_RADIUS_M = 30.0

/** A stop the vehicle reached (or, for the first stop, left) between two matched positions. */
data class StopPass(
    val stopPathIndex: Int,
    val passedAt: Instant,
)

/** Where stop [stopPathIndex] sits along the pattern: the end of its stop path. */
fun stopDistanceM(
    stopPathIndex: Int,
    stopPathCumM: DoubleArray,
    patternLengthM: Double,
): Double = if (stopPathIndex + 1 < stopPathCumM.size) stopPathCumM[stopPathIndex + 1] else patternLengthM

/**
 * The along-trip distance at which stop [stopPathIndex] counts as reached: where it sits, except for
 * the last stop, reached within [LAST_STOP_RADIUS_M] of the end (but never before halfway along its
 * stop path, so a short last stop path can't be "reached" together with the stop before it).
 */
fun stopReachedAtM(
    stopPathIndex: Int,
    stopPathCumM: DoubleArray,
    patternLengthM: Double,
): Double {
    val stopM = stopDistanceM(stopPathIndex, stopPathCumM, patternLengthM)
    if (stopPathIndex + 1 < stopPathCumM.size) return stopM
    return maxOf(patternLengthM - LAST_STOP_RADIUS_M, (stopPathCumM[stopPathIndex] + patternLengthM) / 2)
}

/**
 * Stops passed while the vehicle moved from [prevAlongM] (at [prevTs]) to [alongM] (at [ts]) on
 * one trip. Stop k sits where stop path k ends, so it's reached once the along-trip distance moves
 * from before it to at/after it (see [stopReachedAtM] for the last stop); the pass time is
 * interpolated linearly by distance between the two reports. The first stop (distance 0) is "passed" when the vehicle leaves it - it stood still for
 * an unknown part of that interval, so the midpoint is the least-biased guess.
 */
fun detectStopPasses(
    prevAlongM: Double,
    prevTs: Instant,
    alongM: Double,
    ts: Instant,
    stopPathCumM: DoubleArray,
    patternLengthM: Double,
): List<StopPass> {
    val elapsedMs = Duration.between(prevTs, ts).toMillis()
    if (alongM <= prevAlongM || elapsedMs <= 0) return emptyList()
    val passes = ArrayList<StopPass>()
    for (k in stopPathCumM.indices) {
        val stopM = stopReachedAtM(k, stopPathCumM, patternLengthM)
        val fraction =
            when {
                k == 0 -> if (prevAlongM <= FIRST_STOP_RADIUS_M && alongM > FIRST_STOP_RADIUS_M) 0.5 else continue
                stopM > prevAlongM && stopM <= alongM -> (stopM - prevAlongM) / (alongM - prevAlongM)
                else -> continue
            }
        passes.add(StopPass(k, prevTs.plusMillis((elapsedMs * fraction).toLong())))
    }
    return passes
}
