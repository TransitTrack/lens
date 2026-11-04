package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.avl.AvlProperties

/**
 * Combines a spatial deviation, heading agreement, schedule adherence and a caller-supplied
 * continuity signal into a single `[0, 1]` match score. Used only by the full-inference matcher to
 * rank competing candidate trips.
 */
@Component
class MatchScorer(
    props: AvlProperties,
) {
    private val w = props.match.scoreWeights
    private val maxDev = props.match.maxDeviationM

    /** continuity: 1.0 same trip as prev, 0.5 prev's block's next trip, else 0.0 — caller computes it. */
    fun score(
        deviationM: Double,
        reportBearing: Double?,
        shapeHeading: Double?,
        adherenceSec: Int?,
        continuity: Double,
    ): Double {
        val devTerm = (1.0 - deviationM / maxDev).coerceIn(0.0, 1.0)
        val headingTerm =
            if (reportBearing == null || shapeHeading == null) {
                0.0
            } else {
                1.0 - angularDeltaDeg(reportBearing, shapeHeading) / 180.0
            }
        val schedTerm = if (adherenceSec == null) 0.5 else 1.0 - minOf(1.0, kotlin.math.abs(adherenceSec) / 1800.0)
        return w.deviation * devTerm + w.heading * headingTerm + w.schedule * schedTerm + w.continuity * continuity
    }

    private fun angularDeltaDeg(
        a: Double,
        b: Double,
    ): Double {
        var d = kotlin.math.abs(a - b) % 360.0
        if (d > 180.0) d = 360.0 - d
        return d
    }
}
