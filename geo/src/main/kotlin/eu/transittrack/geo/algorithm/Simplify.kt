package eu.transittrack.geo.algorithm

import eu.transittrack.geo.Position

/**
 * Ramer–Douglas–Peucker line simplification in plain lng/lat degrees (no projection).
 *
 * Drops every point that lies within [toleranceDeg] of the line between its kept neighbours; the
 * first and last points are always kept. Raw GPS-resolution shapes are mostly near-collinear points
 * along straight road segments, so a small tolerance (`0.00003` is roughly 3 m at mid latitudes)
 * removes most points with no visible difference at map zoom levels.
 */
fun simplify(
    points: List<Position>,
    toleranceDeg: Double,
): List<Position> {
    if (points.size <= 2) return points
    val toleranceSq = toleranceDeg * toleranceDeg
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true

    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, points.lastIndex))
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var maxDistSq = 0.0
        var maxIndex = -1
        for (i in start + 1 until end) {
            val d = perpendicularDistanceSq(points[i], points[start], points[end])
            if (d > maxDistSq) {
                maxDistSq = d
                maxIndex = i
            }
        }
        if (maxIndex != -1 && maxDistSq > toleranceSq) {
            keep[maxIndex] = true
            stack.addLast(intArrayOf(start, maxIndex))
            stack.addLast(intArrayOf(maxIndex, end))
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

private fun perpendicularDistanceSq(
    p: Position,
    a: Position,
    b: Position,
): Double {
    val dx = b.lng - a.lng
    val dy = b.lat - a.lat
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0.0) return sqrd(p.lng - a.lng) + sqrd(p.lat - a.lat)
    val t = ((p.lng - a.lng) * dx + (p.lat - a.lat) * dy) / lenSq
    return sqrd(p.lng - (a.lng + t * dx)) + sqrd(p.lat - (a.lat + t * dy))
}
