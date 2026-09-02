package eu.transittrack.gtfs.api.dto

import eu.transittrack.Extent

data class ExtentDto(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    companion object {
        /** `null` when the extent was never populated (all-NaN). */
        fun of(e: Extent?): ExtentDto? =
            if (e == null || e.isEmpty) {
                null
            } else {
                ExtentDto(e.minLat, e.minLon, e.maxLat, e.maxLon)
            }
    }
}
