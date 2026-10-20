package eu.transittrack.schedule.derive

import kotlin.math.max
import kotlin.math.min

import org.locationtech.jts.geom.Coordinate

import eu.transittrack.geo.ProjectedLine

object ShapeProjection {
    fun straightLine(
        a: Coordinate,
        b: Coordinate,
    ): List<Coordinate> = listOf(a, b)

    fun slice(
        line: ProjectedLine,
        fromM: Double,
        toM: Double,
    ): List<Coordinate> {
        val lo = max(0.0, min(fromM, toM))
        val hi = min(line.lengthM, max(fromM, toM))
        val out = ArrayList<Coordinate>()
        out.add(line.pointAt(lo))
        for (i in line.coordinates.indices) {
            if (line.cumulative[i] > lo && line.cumulative[i] < hi) out.add(line.coordinates[i])
        }
        out.add(line.pointAt(hi))
        return out
    }
}
