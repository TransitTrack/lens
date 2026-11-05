package eu.transittrack.schedule.read

import org.springframework.stereotype.Service

import eu.transittrack.geo.Feature
import eu.transittrack.geo.FeatureCollection
import eu.transittrack.geo.Line
import eu.transittrack.geo.Point
import eu.transittrack.geo.algorithm.simplify
import eu.transittrack.geo.json.toGeoJsonBase64
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.schedule.model.TripPatternRepository

/**
 * Builds the whole-feed network layer (trip-pattern polylines + boarding stops) as one GeoJSON
 * `FeatureCollection`, returned as `base64(gzip(geojson))`.
 *
 * Every feature carries a `kind` property: `"pattern"` (LineString) or `"stop"` (Point).
 */
@Service
class FeedGeometryService(
    private val patterns: TripPatternRepository,
    private val routes: RouteRepository,
    private val stops: StopRepository,
    private val resolver: RevisionResolver,
) {
    fun feedGeometry(
        feedCode: String,
        revisionId: String?,
    ): String {
        val rev = resolver.resolve(feedCode, revisionId)
        val routesById = routes.findByRevisionId(rev).associateBy { it.routeId }

        val lines =
            patterns.findByRevisionId(rev).mapNotNull { p ->
                val coords = simplify(p.geometry?.coordinates.orEmpty(), SIMPLIFY_TOLERANCE_DEG)
                if (coords.size < 2) return@mapNotNull null
                val route = routesById[p.routeId]
                Feature(
                    Line(coords),
                    properties =
                        listOfNotNull(
                            "kind" to "pattern",
                            "patternKey" to p.patternKey,
                            "routeId" to p.routeId,
                            p.headsign?.let { "headsign" to it },
                            "tripCount" to p.tripCount,
                            route?.routeShortName?.let { "routeShortName" to it },
                            route?.routeLongName?.let { "routeLongName" to it },
                            route?.routeType?.let { "routeType" to it },
                            route?.routeColor?.let { "routeColor" to it },
                        ).toMap(),
                )
            }

        val points =
            stops.findByRevisionId(rev).mapNotNull { s ->
                val lat = s.stopLat ?: return@mapNotNull null
                val lon = s.stopLon ?: return@mapNotNull null
                if ((s.locationType ?: 0) != 0 || !validPosition(lon, lat)) return@mapNotNull null
                Feature(
                    Point(lat, lon),
                    properties = mapOf("kind" to "stop", "stopId" to s.stopId, "name" to (s.stopName ?: s.stopId)),
                )
            }

        return FeatureCollection(lines + points).toGeoJsonBase64()
    }

    private fun validPosition(
        lng: Double,
        lat: Double,
    ) = lng in -180.0..180.0 && lat in -90.0..90.0

    private companion object {
        /** ~3 m at mid latitudes; matches what the frontend used to apply. */
        const val SIMPLIFY_TOLERANCE_DEG = 0.00003
    }
}
