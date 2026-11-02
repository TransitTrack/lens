package eu.transittrack.schedule.derive

/** Headsign precedence, per TheTransitClock `GtfsData.createNewTrip`: trip -> first stop -> "Loop". */
object Headsigns {
    private const val DEFAULT = "Loop"

    fun resolve(
        tripHeadsign: String?,
        firstStopHeadsign: String?,
    ): String =
        tripHeadsign?.ifBlank { null }
            ?: firstStopHeadsign?.ifBlank { null }
            ?: DEFAULT
}
