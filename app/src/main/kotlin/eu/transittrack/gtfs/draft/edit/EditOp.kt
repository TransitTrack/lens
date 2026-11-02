package eu.transittrack.gtfs.draft.edit

import java.util.concurrent.ConcurrentHashMap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

import eu.transittrack.gtfs.model.CalendarDateRepository
import eu.transittrack.gtfs.model.CalendarRepository
import eu.transittrack.gtfs.model.FrequencyRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.TripPatternRepository

/** Thrown when the caller's `expectedVersion` no longer matches the draft (concurrent edit). */
class StaleDraftException(
    val currentVersion: Long,
) : RuntimeException("draft was modified concurrently (now v$currentVersion)")

/** Thrown when the caller no longer holds the editor lock on the draft (taken over, or expired). */
class LockNotHeldException(
    val draftId: Long,
) : RuntimeException("editor lock not held for draft $draftId")

/** Everything an [EditOp] or a reversible builder needs to inspect and mutate a draft revision. */
data class EditContext(
    val revisionId: Long,
    val stopTimes: StopTimeRepository,
    val trips: TripRepository,
    val frequencies: FrequencyRepository,
    val tripPatterns: TripPatternRepository,
    val calendars: CalendarRepository,
    val calendarDates: CalendarDateRepository,
    val json: Json,
)

/**
 * The result of planning an edit: a human summary, the forward/inverse direction JSON that gets
 * journalled, and the mutation to run inside the apply transaction.
 */
data class PlannedEdit(
    val summary: String,
    val forward: JsonElement,
    val inverse: JsonElement,
    val mutate: () -> Unit,
)

interface EditOp {
    val op: String

    /** When true, `apply` refuses to run against a draft whose derivation is stale. */
    val needsFreshDerivation: Boolean get() = false

    fun plan(ctx: EditContext): PlannedEdit
}

/**
 * Rebuilds a mutation lambda from a journal row's stored direction JSON (used by undo/redo).
 * Each concrete op registers its builder here (in later tasks); tests register throwaway ops.
 */
object EditOpRegistry {
    private val builders = ConcurrentHashMap<String, (EditContext, JsonElement) -> () -> Unit>()

    fun register(
        op: String,
        builder: (EditContext, JsonElement) -> () -> Unit,
    ) {
        builders[op] = builder
    }

    fun mutationFor(
        op: String,
        direction: JsonElement,
    ): (EditContext) -> Unit {
        val b = builders[op] ?: error("no reversible builder for op '$op'")
        return { ctx -> b(ctx, direction)() }
    }
}
