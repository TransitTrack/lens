package eu.transittrack.gtfs.revision

/**
 * Published when the derived schedule rows a revision exposes have changed — on activation (a new
 * revision becomes the active one) and on re-derivation (the same revision's derived tables are
 * rebuilt in place). Consumers that cache anything keyed by `revisionId` must invalidate on this.
 */
data class RevisionDerivedRowsChangedEvent(
    val revisionId: Long,
)
