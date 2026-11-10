package eu.transittrack.schedule.model

import eu.transittrack.gtfs.model.RevisionScoped

/** A trip's position within its block. Persisted by `BlockTripTable` in core. */
class BlockTrip(
    revisionId: Long,
    val blockId: Long,
    val tripId: Long,
    val listIndex: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
) : RevisionScoped(revisionId)
