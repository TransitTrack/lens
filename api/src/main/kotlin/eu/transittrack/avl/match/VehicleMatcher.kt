package eu.transittrack.avl.match

interface VehicleMatcher {
    val mode: AvlAssignmentMode

    fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome
}
