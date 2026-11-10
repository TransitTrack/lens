package eu.transittrack.predict.generate

/**
 * One stop, on one trip, within a vehicle's remaining prediction horizon. [remainingFraction] is
 * the share of this stop's stop path the vehicle still has to cover: below 1.0 only for the first
 * horizon stop, the stop path the vehicle is currently on. [tripHop] is zero on the matched trip,
 * positive on a following block trip, even when the matched trip has no stops left in the horizon.
 */
data class HorizonStop(
    val tripRowId: Long,
    val tripPatternId: Long,
    val stopPathIndex: Int,
    val remainingFraction: Double = 1.0,
    val tripHop: Int = 0,
)
