package eu.transittrack.avl.match

import org.springframework.stereotype.Component

import eu.transittrack.AvlAssignmentMode

/**
 * Tries [TrustDescriptorMatcher] first and falls back to [FullInferenceMatcher] whenever the
 * descriptor path does not produce a match — for feeds whose descriptors are usually present but
 * not always reliable.
 */
@Component
class DescriptorThenInferMatcher(
    private val trust: TrustDescriptorMatcher,
    private val infer: FullInferenceMatcher,
) : VehicleMatcher {
    override val mode = AvlAssignmentMode.DESCRIPTOR_THEN_INFER

    override fun match(
        report: AvlReportView,
        prev: VehicleStateView?,
        ctx: MatchContext,
    ): MatchOutcome =
        trust
            .match(report, prev, ctx)
            .let {
                it as? MatchOutcome.Matched ?: infer.match(report, prev, ctx)
            }
}
