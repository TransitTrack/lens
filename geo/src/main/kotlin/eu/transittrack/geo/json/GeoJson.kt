package eu.transittrack.geo.json

import eu.transittrack.geo.BBox
import eu.transittrack.geo.Feature
import eu.transittrack.geo.FeatureCollection
import eu.transittrack.geo.FeatureId
import eu.transittrack.geo.GeoJsonObject
import eu.transittrack.geo.Geometry
import eu.transittrack.geo.GeometryCollection
import eu.transittrack.geo.Line
import eu.transittrack.geo.MultiLineString
import eu.transittrack.geo.MultiPoint
import eu.transittrack.geo.MultiPolygon
import eu.transittrack.geo.NumberFeatureId
import eu.transittrack.geo.Point
import eu.transittrack.geo.Polygon
import eu.transittrack.geo.Position
import eu.transittrack.geo.StringFeatureId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * RFC 7946 GeoJSON codec for the geo model.
 *
 * Three representations are supported, all round-trippable:
 * - [JsonElement] tree: [encode] / [decode]
 * - GeoJSON text (e.g. for DB storage): [toJson] / [fromJson]
 * - compact transport form, `base64(gzip(geojson text))`: [toCompact] / [fromCompact]
 *
 * Decoding goes through the model constructors, so the usual validators (coordinate ranges,
 * closed rings, ...) apply to parsed input. [Feature.properties] values may be strings, numbers,
 * booleans, lists and nested maps; JSON `null` property values are dropped when decoding because the
 * model's map is non-null, and any other value type fails encoding with a [SerializationException].
 */
object GeoJson {
    private val json = Json { encodeDefaults = true }

    // ---- text / compact -------------------------------------------------------------------

    fun toJson(obj: GeoJsonObject): String = json.encodeToString(JsonElement.serializer(), encode(obj))

    fun fromJson(text: String): GeoJsonObject = decode(json.parseToJsonElement(text))

    /** `base64(gzip(utf8(geojson)))`, standard base64 alphabet with padding. */
    fun toCompact(obj: GeoJsonObject): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(toJson(obj).toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    fun fromCompact(encoded: String): GeoJsonObject {
        val bytes =
            try {
                GZIPInputStream(Base64.getDecoder().decode(encoded).inputStream()).use { it.readBytes() }
            } catch (e: IOException) {
                throw SerializationException("Invalid compact GeoJSON payload", e)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Invalid compact GeoJSON payload", e)
            }
        return fromJson(bytes.toString(Charsets.UTF_8))
    }

    // ---- encode -----------------------------------------------------------------------------

    fun encode(obj: GeoJsonObject): JsonObject =
        when (obj) {
            is Geometry<*> -> encodeGeometry(obj)
            is GeometryCollection ->
                node("GeometryCollection", obj.bbox) {
                    put("geometries", JsonArray(obj.geometries.map { encodeGeometry(it) }))
                }
            is Feature ->
                node("Feature", obj.bbox) {
                    put("geometry", encodeGeometry(obj.geometry))
                    put("properties", obj.properties?.let { encodeValue(it) } ?: JsonNull)
                    obj.id?.let { put("id", encodeId(it)) }
                }
            is FeatureCollection ->
                node("FeatureCollection", obj.bbox) {
                    put("features", JsonArray(obj.features.map { encode(it) }))
                }
            else -> throw SerializationException("Unsupported GeoJSON object: ${obj::class.simpleName}")
        }

    private fun encodeGeometry(g: Geometry<*>): JsonObject =
        node(g.type, g.bbox) { put("coordinates", encodeCoordinates(g.coordinates)) }

    private fun node(
        type: String,
        bbox: BBox?,
        extra: JsonObjectBuilder.() -> Unit,
    ): JsonObject =
        buildJsonObject {
            put("type", type)
            bbox?.let { put("bbox", encodeBBox(it)) }
            extra()
        }

    private fun encodeCoordinates(c: Any?): JsonElement =
        when (c) {
            is Position -> JsonArray(listOfNotNull(c.lng, c.lat, c.alt).map { JsonPrimitive(it) })
            is List<*> -> JsonArray(c.map { encodeCoordinates(it) })
            else -> throw SerializationException("Unsupported coordinates: $c")
        }

    private fun encodeBBox(b: BBox): JsonArray =
        JsonArray(
            if (b.minAlt != null && b.maxAlt != null) {
                listOf(b.minLng, b.minLat, b.minAlt, b.maxLng, b.maxLat, b.maxAlt)
            } else {
                listOf(b.minLng, b.minLat, b.maxLng, b.maxLat)
            }.map { JsonPrimitive(it) },
        )

    private fun encodeId(id: FeatureId): JsonPrimitive =
        when (id) {
            is StringFeatureId -> JsonPrimitive(id.value)
            is NumberFeatureId -> JsonPrimitive(id.value)
        }

    private fun encodeValue(v: Any?): JsonElement =
        when (v) {
            null -> JsonNull
            is String -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to encodeValue(x) })
            is Iterable<*> -> JsonArray(v.map { encodeValue(it) })
            else -> throw SerializationException("Unsupported property value type: ${v::class.qualifiedName}")
        }

    // ---- decode -----------------------------------------------------------------------------

    fun decode(element: JsonElement): GeoJsonObject {
        val o = element as? JsonObject ?: throw SerializationException("GeoJSON must be a JSON object")
        val bbox = o["bbox"]?.takeUnless { it is JsonNull }?.let(::decodeBBox)
        return when (val type = o.string("type")) {
            "Point" -> Point(position(o.coordinates()), bbox)
            "MultiPoint" -> MultiPoint(positions(o.coordinates()), bbox)
            "LineString" -> Line(positions(o.coordinates()), bbox)
            "MultiLineString" -> MultiLineString(o.coordinates().array().map(::positions), bbox)
            "Polygon" -> Polygon(o.coordinates().array().map(::positions), bbox)
            "MultiPolygon" ->
                MultiPolygon(o.coordinates().array().map { p -> p.array().map(::positions) }, bbox)
            "GeometryCollection" ->
                GeometryCollection(
                    (o["geometries"] ?: missing("geometries")).array().map(::decodeGeometry),
                    bbox,
                )
            "Feature" ->
                Feature(
                    geometry = decodeGeometry(o["geometry"].takeUnless { it is JsonNull } ?: missing("geometry")),
                    bbox = bbox,
                    properties = (o["properties"] as? JsonObject)?.let(::decodeProperties),
                    id = o["id"]?.takeUnless { it is JsonNull }?.let(::decodeId),
                )
            "FeatureCollection" ->
                FeatureCollection(
                    (o["features"] ?: missing("features")).array().map {
                        decode(it) as? Feature
                            ?: throw SerializationException("FeatureCollection may only contain Features")
                    },
                    bbox,
                )
            else -> throw SerializationException("Unknown GeoJSON type: $type")
        }
    }

    private fun decodeGeometry(e: JsonElement): Geometry<*> =
        decode(e) as? Geometry<*> ?: throw SerializationException("Expected a GeoJSON geometry")

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: missing(key)

    private fun JsonObject.coordinates(): JsonElement = this["coordinates"] ?: missing("coordinates")

    private fun missing(key: String): Nothing = throw SerializationException("Missing or invalid GeoJSON member '$key'")

    private fun JsonElement.array(): JsonArray = this as? JsonArray ?: throw SerializationException("Expected a JSON array")

    private fun number(e: JsonElement): Double =
        (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            ?: throw SerializationException("Expected a number, got $e")

    private fun position(e: JsonElement): Position {
        val a = e.array()
        if (a.size !in 2..3) throw SerializationException("A position needs 2 or 3 numbers, got ${a.size}")
        return Position(number(a[0]), number(a[1]), a.getOrNull(2)?.let(::number))
    }

    private fun positions(e: JsonElement): List<Position> = e.array().map(::position)

    private fun decodeBBox(e: JsonElement): BBox {
        val n = e.array().map(::number)
        return when (n.size) {
            4 -> BBox(n[0], n[1], n[2], n[3])
            6 -> BBox(n[0], n[1], n[3], n[4], n[2], n[5])
            else -> throw SerializationException("bbox needs 4 or 6 numbers, got ${n.size}")
        }
    }

    private fun decodeId(e: JsonElement): FeatureId {
        val p = e as? JsonPrimitive ?: throw SerializationException("Feature id must be a string or number")
        return if (p.isString) StringFeatureId(p.content) else NumberFeatureId(decodeNumber(p))
    }

    private fun decodeNumber(p: JsonPrimitive): Number = p.longOrNull ?: p.double

    private fun decodeProperties(o: JsonObject): Map<String, Any> =
        o.entries.mapNotNull { (k, v) -> decodeValue(v)?.let { k to it } }.toMap()

    private fun decodeValue(e: JsonElement): Any? =
        when (e) {
            is JsonNull -> null
            is JsonPrimitive ->
                when {
                    e.isString -> e.content
                    e.content == "true" || e.content == "false" -> e.content.toBoolean()
                    else -> decodeNumber(e)
                }
            is JsonArray -> e.mapNotNull(::decodeValue)
            is JsonObject -> decodeProperties(e)
        }
}

/** GeoJSON text for storage. */
fun GeoJsonObject.toGeoJson(): String = GeoJson.toJson(this)

/** `base64(gzip(geojson))` for compact transport to the frontend. */
fun GeoJsonObject.toGeoJsonBase64(): String = GeoJson.toCompact(this)

fun geoJsonObjectFromJson(text: String): GeoJsonObject = GeoJson.fromJson(text)

fun geoJsonObjectFromBase64(encoded: String): GeoJsonObject = GeoJson.fromCompact(encoded)

/**
 * kotlinx [KSerializer] so a [GeoJsonObject] can be embedded in other `@Serializable` types or used
 * with a `Json` instance (e.g. Spring's kotlinx converter). Only works with JSON formats.
 */
object GeoJsonObjectSerializer : KSerializer<GeoJsonObject> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: GeoJsonObject,
    ) {
        val e = encoder as? JsonEncoder ?: throw SerializationException("GeoJSON can only be encoded to JSON")
        e.encodeJsonElement(GeoJson.encode(value))
    }

    override fun deserialize(decoder: Decoder): GeoJsonObject {
        val d = decoder as? JsonDecoder ?: throw SerializationException("GeoJSON can only be decoded from JSON")
        return GeoJson.decode(d.decodeJsonElement())
    }
}
