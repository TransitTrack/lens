package eu.transittrack.bench

/** One benchmark-fixture trip: its GTFS id, route, and the shape points it runs along (in order). */
data class BenchTrip(
    val tripId: String,
    val routeId: String,
    val points: List<Pair<Double, Double>>,
)

/**
 * Generates a small, self-contained GTFS static schedule in memory - no fixture files on disk to
 * maintain. [ROUTE_COUNT] routes, each with [TRIPS_PER_ROUTE] trips (spread across the day) running
 * the same [SHAPE_POINTS]-point shape, with [STOP_COUNT] stops sampled evenly along it.
 *
 * The single calendar service covers every day of the week for 2020-2035 - unlike a real feed
 * (see the `poznan` AVL matching investigation this session), a benchmark run should never be able
 * to fail matching just because "today" falls outside the fixture's service window.
 */
object BenchmarkGtfsFixture {
    const val ROUTE_COUNT = 3
    const val TRIPS_PER_ROUTE = 4
    const val SHAPE_POINTS = 15
    const val STOP_COUNT = 5
    private val START_TIMES_SEC = listOf(0, 6 * 3600, 12 * 3600, 18 * 3600)

    data class Generated(
        val files: Map<String, String>,
        val trips: List<BenchTrip>,
    )

    fun generate(): Generated {
        val trips = mutableListOf<BenchTrip>()
        val agency = "agency_id,agency_name,agency_url,agency_timezone\nAG,Bench,https://example.org,Etc/UTC\n"
        val feedInfo = "feed_publisher_name,feed_publisher_url,feed_lang,feed_version\nBench,https://example.org,en,bench\n"
        val calendar =
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
                "EVERY,1,1,1,1,1,1,1,20200101,20351231\n"

        val routes = StringBuilder("route_id,agency_id,route_short_name,route_long_name,route_type\n")
        val shapes = StringBuilder("shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n")
        val stops = StringBuilder("stop_id,stop_name,stop_lat,stop_lon\n")
        val tripRows = StringBuilder("route_id,service_id,trip_id,trip_headsign,direction_id,block_id,shape_id\n")
        val stopTimes = StringBuilder("trip_id,arrival_time,departure_time,stop_id,stop_sequence,timepoint\n")

        for (r in 0 until ROUTE_COUNT) {
            val routeId = "R${r + 1}"
            val shapeId = "SHP$routeId"
            routes.append("$routeId,AG,$routeId,Route $routeId,3\n")

            val baseLat = 52.00 + r * 0.05
            val points = (0 until SHAPE_POINTS).map { i -> baseLat to (17.000 + i * 0.005) }
            points.forEachIndexed { i, (lat, lon) -> shapes.append("$shapeId,$lat,$lon,${i + 1}\n") }

            val stopPointIndexes = (0 until STOP_COUNT).map { s -> s * (SHAPE_POINTS - 1) / (STOP_COUNT - 1) }
            stopPointIndexes.forEachIndexed { s, idx ->
                val (lat, lon) = points[idx]
                stops.append("${routeId}_S${s + 1},$routeId Stop ${s + 1},$lat,$lon\n")
            }

            for ((t, startSec) in START_TIMES_SEC.withIndex()) {
                val tripId = "T-$routeId-${t + 1}"
                tripRows.append("$routeId,EVERY,$tripId,To end,0,,$shapeId\n")
                stopPointIndexes.forEachIndexed { s, _ ->
                    val timeSec = startSec + s * 180
                    val time = "%02d:%02d:%02d".format(timeSec / 3600, (timeSec % 3600) / 60, timeSec % 60)
                    stopTimes.append("$tripId,$time,$time,${routeId}_S${s + 1},${s + 1},1\n")
                }
                trips += BenchTrip(tripId, routeId, points)
            }
        }

        val files =
            mapOf(
                "agency.txt" to agency,
                "feed_info.txt" to feedInfo,
                "calendar.txt" to calendar,
                "routes.txt" to routes.toString(),
                "shapes.txt" to shapes.toString(),
                "stops.txt" to stops.toString(),
                "trips.txt" to tripRows.toString(),
                "stop_times.txt" to stopTimes.toString(),
            )
        return Generated(files, trips)
    }
}
