package eu.transittrack.avl.match

import org.locationtech.jts.geom.Coordinate

import eu.transittrack.geo.ProjectedLine

/** A trip pattern's shape as one polyline, plus where each stop path begins along it (metres). */
@Suppress("ArrayInDataClass")
data class PatternGeometry(
    val line: ProjectedLine,
    val stopPathCumM: DoubleArray,
    /** Parallel to [stopPathCumM]: whether that stop path ends in a scheduled layover. */
    val layoverStop: BooleanArray,
) {
    /** End-of-path location for stop path [i] — where it ends along [line]. */
    fun endOfPath(i: Int): Coordinate = line.pointAt(if (i + 1 < stopPathCumM.size) stopPathCumM[i + 1] else line.lengthM)
}
