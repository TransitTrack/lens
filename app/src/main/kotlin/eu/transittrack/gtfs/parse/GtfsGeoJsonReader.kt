package eu.transittrack.gtfs.parse

import java.io.InputStream

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import eu.transittrack.ParseException
import eu.transittrack.ProjectJson

data class ParsedLocation(
    val locationId: String,
    val stopName: String?,
    val stopDesc: String?,
    val geometryJson: String,
)

object GtfsGeoJsonReader {
    fun read(input: InputStream): List<ParsedLocation> {
        val root = ProjectJson.parseToJsonElement(input.reader(Charsets.UTF_8).readText()) as? JsonObject
            ?: throw ParseException("locations.geojson root must be a FeatureCollection")
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") {
            throw ParseException("locations.geojson root must be a FeatureCollection")
        }
        val features = root["features"]?.takeUnless { it is JsonNull }?.jsonArray ?: return emptyList()
        return buildList {
            for (feature in features) {
                val f = feature.jsonObject
                val id =
                    f["id"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                        ?: throw ParseException("locations.geojson feature missing 'id'")
                val props = f["properties"]?.takeUnless { it is JsonNull }?.jsonObject
                add(
                    ParsedLocation(
                        locationId = id,
                        stopName = props
                            ?.get("stop_name")
                            ?.takeUnless { it is JsonNull }
                            ?.jsonPrimitive
                            ?.content,
                        stopDesc = props
                            ?.get("stop_desc")
                            ?.takeUnless { it is JsonNull }
                            ?.jsonPrimitive
                            ?.content,
                        geometryJson =
                            f["geometry"]?.toString() ?: "null",
                    ),
                )
            }
        }
    }
}
