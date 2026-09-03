package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.model.StopTime

/** One stop visit after consecutive-duplicate collapsing. */
data class CleanStopTime(
    val stopId: String,
    val stopSequence: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
    val stopHeadsign: String?,
    val pickupType: Int?,
    val dropOffType: Int?,
    val timepoint: Int?,
    val waitReconstructed: Boolean,
)

/**
 * Port of TheTransitClock `GtfsData.processStopTimesForTrip`. When the same `stop_id` appears on
 * consecutive `stop_times` rows:
 *  - equal times (or the second row has none) -> the second row is a GTFS artifact, dropped.
 *  - differing times -> a real hold at one physical stop; collapse to one wait stop carrying the
 *    first row's arrival and the second row's departure.
 * Rows with a null `stop_id` pass through untouched (the caller filters trips that contain any).
 */
object StopTimeCleaner {
    fun clean(rows: List<StopTime>): List<CleanStopTime> {
        val out = ArrayList<CleanStopTime>(rows.size)
        for (r in rows) {
            val prev = out.lastOrNull()
            if (prev != null && r.stopId != null && prev.stopId == r.stopId) {
                val secondTimeless = r.arrivalTime == null && r.departureTime == null
                val sameTimes = prev.arrivalSec == r.arrivalTime && prev.departureSec == r.departureTime
                if (secondTimeless || sameTimes) {
                    continue // drop the duplicate
                }
                out[out.lastIndex] = prev.copy(
                    departureSec = r.departureTime ?: prev.departureSec,
                    waitReconstructed = true,
                )
                continue
            }
            out.add(
                CleanStopTime(
                    stopId = r.stopId ?: "",
                    stopSequence = r.stopSequence,
                    arrivalSec = r.arrivalTime,
                    departureSec = r.departureTime,
                    stopHeadsign = r.stopHeadsign,
                    pickupType = r.pickupType,
                    dropOffType = r.dropOffType,
                    timepoint = r.timepoint,
                    waitReconstructed = false,
                ),
            )
        }
        return out
    }
}
