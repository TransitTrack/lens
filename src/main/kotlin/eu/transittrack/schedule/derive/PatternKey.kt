package eu.transittrack.schedule.derive

import java.security.MessageDigest

/**
 * Deterministic identity for a trip pattern: the TransitClock-style key
 * `{routeId}|{shapeId}|{firstStop}_to_{lastStop}|{hash of the full ordered stop list}`. Two trips
 * **on the same route** with the same shape visiting the same stops in the same order collapse onto
 * one pattern. The route is part of the identity because `trip_pattern.route_id` is single-valued
 * and the read API exposes patterns scoped by route.
 */
object PatternKey {
    fun of(
        routeId: String,
        shapeId: String?,
        stopIds: List<String>,
    ): String {
        require(stopIds.isNotEmpty()) { "stopIds must not be empty" }
        val hash = sha1Hex(stopIds.joinToString(",")).take(12)
        return "$routeId|${shapeId ?: "-"}|${stopIds.first()}_to_${stopIds.last()}|$hash"
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}
