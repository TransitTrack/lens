package eu.transittrack.gtfs.model

/** GTFS `routes.route_type` values. */
object RouteTypes {
    const val TRAM = 0
    const val SUBWAY = 1
    const val RAIL = 2
    const val BUS = 3
    const val FERRY = 4
    const val CABLE_TRAM = 5
    const val AERIAL_LIFT = 6
    const val FUNICULAR = 7
    const val TROLLEYBUS = 11
    const val MONORAIL = 12

    /**
     * Maps an extended route type (the Google/HVT values GTFS allows, e.g. `704` local bus) to the basic
     * type for the same kind of vehicle. Basic types, and extended types with no basic equivalent
     * (air, taxi, miscellaneous), are returned unchanged.
     */
    fun basic(routeType: Int): Int =
        when (routeType) {
            in 100..199 -> RAIL

            // railway services
            in 200..299 -> BUS

            // coach services
            in 300..399 -> RAIL

            // suburban railway services
            405 -> MONORAIL

            in 400..699 -> SUBWAY

            // urban railway, metro and underground services
            in 700..799 -> BUS

            in 800..899 -> TROLLEYBUS

            in 900..999 -> TRAM

            in 1000..1099 -> FERRY

            // water transport services
            in 1200..1299 -> FERRY

            in 1300..1399 -> AERIAL_LIFT

            in 1400..1499 -> FUNICULAR

            else -> routeType
        }
}
