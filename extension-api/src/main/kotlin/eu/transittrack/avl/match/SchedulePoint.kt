package eu.transittrack.avl.match

data class SchedulePoint(
    val stopPathIndex: Int,
    val arrivalSec: Int?,
    val departureSec: Int?,
)
