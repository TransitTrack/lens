package eu.transittrack.avl.match

import eu.transittrack.Point
import eu.transittrack.Polyline

/** A trip pattern's shape as one polyline, plus where each stop path begins along it (metres). */
@Suppress("ArrayInDataClass")
data class PatternGeometry(
    val line: Polyline,
    val stopPathCumM: DoubleArray,
    /** Parallel to [stopPathCumM]: whether that stop path ends in a scheduled layover. */
    val layoverStop: BooleanArray,
) {
    /** End-of-path location for stop path [i] — where it ends along [line]. */
    fun endOfPath(i: Int): Point = line.pointAt(if (i + 1 < stopPathCumM.size) stopPathCumM[i + 1] else line.lengthM)
}
