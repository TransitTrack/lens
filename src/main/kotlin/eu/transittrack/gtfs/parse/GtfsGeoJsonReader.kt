package eu.transittrack.gtfs.parse

import java.io.InputStream

import tools.jackson.databind.json.JsonMapper

import eu.transittrack.exception.ParseException

data class ParsedLocation(
    val locationId: String,
    val stopName: String?,
    val stopDesc: String?,
    val geometryJson: String,
)

object GtfsGeoJsonReader {
    private val mapper = JsonMapper.builder().build()

    fun read(input: InputStream): List<ParsedLocation> {
        val root = mapper.readTree(input)
        if (root.get("type")?.asString() != "FeatureCollection") {
            throw ParseException("locations.geojson root must be a FeatureCollection")
        }
        val features = root.get("features") ?: return emptyList()
        return buildList {
            for (f in features) {
                val id =
                    f.get("id")?.takeIf { !it.isNull }?.asString()
                        ?: throw ParseException("locations.geojson feature missing 'id'")
                val props = f.get("properties")
                add(
                    ParsedLocation(
                        locationId = id,
                        stopName = props?.get("stop_name")?.takeIf { !it.isNull }?.asString(),
                        stopDesc = props?.get("stop_desc")?.takeIf { !it.isNull }?.asString(),
                        geometryJson =
                            f.get("geometry")?.takeIf { !it.isNull }?.let { mapper.writeValueAsString(it) } ?: "null",
                    ),
                )
            }
        }
    }
}
