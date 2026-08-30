package eu.transittrack.schedule.derive

import java.security.MessageDigest

/**
 * Deterministic identity for a trip pattern: the TransitClock-style key
 * `{shapeId}|{firstStop}_to_{lastStop}|{hash of the full ordered stop list}`.
 * Two trips with the same shape visiting the same stops in the same order
 * collapse onto one pattern.
 */
object PatternKey {
    fun of(shapeId: String?, stopIds: List<String>): String {
        require(stopIds.isNotEmpty()) { "stopIds must not be empty" }
        val hash = sha1Hex(stopIds.joinToString(",")).take(12)
        return "${shapeId ?: "-"}|${stopIds.first()}_to_${stopIds.last()}|$hash"
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
