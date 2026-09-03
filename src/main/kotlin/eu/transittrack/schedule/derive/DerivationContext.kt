package eu.transittrack.schedule.derive

import java.util.concurrent.ConcurrentHashMap

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.Extent

/** Per-stop visit after cleaning + the trip's resolved schedule; filled across the derive stages. */
class DerivationState {
    val patternIdByKey = HashMap<String, Long>()
    val patternStopPathIds = HashMap<Long, List<Long>>()
    val patternCumDist = HashMap<Long, DoubleArray>()
    val patternExtents = HashMap<String, Extent>()
    val cleanedRows = HashMap<String, List<CleanStopTime>>()
    val derivedTrips = ArrayList<DerivedTrip>()
}

class DerivedTrip(
    var schedTripId: Long,
    val tripId: String,
    val patternId: Long,
    val blockId: String?,
    val serviceId: String,
    val routeId: String,
    val startSec: Int,
    val endSec: Int,
    val firstStopId: String,
    val lastStopId: String,
    val frequencyBased: Boolean,
    val noSchedule: Boolean,
    val resolved: List<ResolvedScheduleTime>,
)

/**
 * In-flight state shared by the ordered schedule-derivation post-processors, keyed by
 * `revisionId` (ingests of different feeds run concurrently on the ingest executor). One revision's
 * processors run sequentially on one thread, so [DerivationState] itself is not synchronised.
 */
@Component
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class DerivationContext {
    private val states = ConcurrentHashMap<Long, DerivationState>()

    fun open(revisionId: Long): DerivationState {
        val fresh = DerivationState()
        check(states.putIfAbsent(revisionId, fresh) == null) {
            "derivation context already open for revision $revisionId"
        }
        return fresh
    }

    fun get(revisionId: Long): DerivationState = states[revisionId] ?: error("no derivation context open for revision $revisionId")

    fun close(revisionId: Long) {
        states.remove(revisionId)
    }

    fun size(): Int = states.size
}
