package eu.transittrack.avl.match.cache

/** Single source of truth for the AVL match read-cache names. Read by CacheConfiguration and RevisionService. */
object AvlCaches {
    const val TRIP_BY_ROW_ID = "avlTripByRowId"
    const val TRIP_BY_GTFS_ID = "avlTripByGtfsId"
    const val TRIPS_BY_SERVICES = "avlTripsByServices"
    const val TRIPS_BY_ROUTE_SERVICES = "avlTripsByRouteServices"
    const val TRIP_PATTERN = "avlTripPattern"
    const val STOP_PATHS = "avlStopPaths"
    const val SCHEDULE = "avlSchedule"
    const val BLOCK_TRIP_BY_TRIP = "avlBlockTripByTrip"
    const val BLOCK_TRIPS_BY_BLOCK = "avlBlockTripsByBlock"
    const val SERVICE_IDS = "avlServiceIds"
    const val PATTERN_GEOMETRY = "avlPatternGeometry"
    const val AGENCY_TIMEZONE = "avlAgencyTimezone"

    val NAMES: List<String> =
        listOf(
            TRIP_BY_ROW_ID,
            TRIP_BY_GTFS_ID,
            TRIPS_BY_SERVICES,
            TRIPS_BY_ROUTE_SERVICES,
            TRIP_PATTERN,
            STOP_PATHS,
            SCHEDULE,
            BLOCK_TRIP_BY_TRIP,
            BLOCK_TRIPS_BY_BLOCK,
            SERVICE_IDS,
            PATTERN_GEOMETRY,
            AGENCY_TIMEZONE,
        )
}
