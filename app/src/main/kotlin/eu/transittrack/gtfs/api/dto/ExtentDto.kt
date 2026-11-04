package eu.transittrack.gtfs.api.dto

import eu.transittrack.geo.BBox

data class ExtentDto(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    companion object {
        /** `null` when the extent was never populated. */
        fun of(e: BBox?): ExtentDto? = e?.let { ExtentDto(it.minLat, it.minLng, it.maxLat, it.maxLng) }
    }
}
