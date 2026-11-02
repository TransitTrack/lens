package eu.transittrack.schedule.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable

/** How a travel/dwell time was determined. Only schedule-derived today; AVL etc. later. */
enum class TravelTimeSource { SCHEDULE, }

/**
 * Typical (median) scheduled travel + dwell time for one stop path of a trip pattern, in seconds.
 * One row per `(revision_id, trip_pattern_id, stop_path_index)`. Replaces the former
 * `stop_path.typical_travel_time_sec` / `typical_dwell_time_sec` columns.
 */
class TravelTimesForStopPath(
    revisionId: Long,
    var tripPatternId: Long,
    var stopPathIndex: Int,
    var travelTimeSec: Int?,
    var dwellTimeSec: Int?,
    var howSet: TravelTimeSource = TravelTimeSource.SCHEDULE,
) : RevisionScoped(revisionId)

object TravelTimesForStopPathTable :
    RevisionScopedTable<TravelTimesForStopPath>("travel_times_for_stop_path", TravelTimesForStopPath::class.java, "fk_tt_stop_path_rev") {
    val tripPatternId =
        long("trip_pattern_id").references(
            TripPatternTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_tt_stop_path_pattern",
        )
    val stopPathIndex = integer("stop_path_index")
    val travelTimeSec = integer("travel_time_sec").nullable()
    val dwellTimeSec = integer("dwell_time_sec").nullable()

    /** [TravelTimeSource] by ordinal. */
    val howSet = short("how_set").default(0.toShort())

    init {
        uniqueIndex("uq_tt_stop_path_rev_pattern_idx", revisionId, tripPatternId, stopPathIndex)
        index("ix_tt_stop_path_rev_pattern", false, revisionId, tripPatternId)
        index("ix_tt_stop_path_pattern", false, tripPatternId)
    }

    override fun read(row: ResultRow) =
        TravelTimesForStopPath(
            revisionId = row[revisionId],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            travelTimeSec = row[travelTimeSec],
            dwellTimeSec = row[dwellTimeSec],
            howSet = TravelTimeSource.entries[row[howSet].toInt()],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: TravelTimesForStopPath,
    ) {
        s[revisionId] = row.revisionId
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[travelTimeSec] = row.travelTimeSec
        s[dwellTimeSec] = row.dwellTimeSec
        s[howSet] = row.howSet.ordinal.toShort()
    }
}
