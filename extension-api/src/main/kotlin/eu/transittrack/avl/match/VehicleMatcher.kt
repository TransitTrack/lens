package eu.transittrack.avl.match

import org.locationtech.jts.geom.Coordinate

import eu.transittrack.AvlAssignmentMode

/** Result of matching one AVL report against the derived schedule. */
sealed interface MatchOutcome {
    data class Matched(
        val tripRowId: Long,
        val blockPk: Long?,
        val tripPatternId: Long,
        val stopPathIndex: Int,
        val distanceAlongTripM: Double,
        val deviationM: Double,
        val scheduleAdherenceSec: Int?,
        val snapped: Coordinate,
        val heading: Double?,
        val score: Double?,
        val revisionId: Long,
    ) : MatchOutcome

    data object Failed : MatchOutcome

    data object Skipped : MatchOutcome
}

interface VehicleMatcher {
    val mode: AvlAssignmentMode

    fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome
}
