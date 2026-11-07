package eu.transittrack.geo.json

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import eu.transittrack.geo.BBox
import eu.transittrack.geo.Feature
import eu.transittrack.geo.FeatureCollection
import eu.transittrack.geo.GeoJsonObject
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
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GeoJsonTest {
    private val ring =
        listOf(Position(0.0, 0.0), Position(10.0, 0.0), Position(10.0, 10.0), Position(0.0, 10.0), Position(0.0, 0.0))
    private val line = Line(listOf(Position(17.0, 51.1), Position(17.01, 51.11, 120.0)))

    private val samples: Map<String, GeoJsonObject> =
        mapOf(
            "point" to Point(51.1, 17.0),
            "pointBbox" to Point(Position(17.0, 51.1), BBox(17.0, 51.1, 17.0, 51.1)),
            "multiPoint" to MultiPoint(listOf(Position(1.0, 2.0), Position(3.0, 4.0))),
            "line" to line,
            "multiLine" to MultiLineString(listOf(line.coordinates, line.coordinates)),
            "polygon" to Polygon(listOf(ring)),
            "multiPolygon" to MultiPolygon(listOf(listOf(ring))),
            "collection" to GeometryCollection(listOf(Point(1.0, 2.0), line)),
            "feature" to
                Feature(
                    Point(51.1, 17.0),
                    BBox(17.0, 51.1, 100.0, 17.0, 51.1, 200.0),
                    mapOf(
                        "name" to "A",
                        "n" to 3L,
                        "d" to 1.5,
                        "ok" to true,
                        "tags" to listOf("x", "y"),
                        "o" to mapOf("k" to 1L),
                    ),
                    StringFeatureId("abc"),
                ),
            "featureNumId" to Feature(Point(1.0, 2.0), id = NumberFeatureId(7L)),
            "featureCollection" to FeatureCollection(listOf(Feature(Point(1.0, 2.0)), Feature(line))),
        )

    @Test
    fun `text round trip for every type`() {
        samples.forEach { (name, obj) ->
            assertThat(GeoJson.fromJson(obj.toGeoJson()), name).isEqualTo(obj)
        }
    }

    @Test
    fun `compact round trip for every type`() {
        samples.forEach { (name, obj) ->
            assertThat(geoJsonObjectFromBase64(obj.toGeoJsonBase64()), name).isEqualTo(obj)
        }
    }

    @Test
    fun `point matches the spec shape`() {
        assertThat(Point(51.1, 17.0).toGeoJson()).isEqualTo("""{"type":"Point","coordinates":[17.0,51.1]}""")
    }

    @Test
    fun `parses a spec style feature`() {
        val f =
            geoJsonObjectFromJson(
                """{"type":"Feature","id":1,"geometry":{"type":"Point","coordinates":[17,51.1]},"properties":{"a":null,"b":"x"}}""",
            ) as Feature
        assertThat(f.id).isEqualTo(NumberFeatureId(1L))
        assertThat(f.properties).isEqualTo(mapOf("b" to "x"))
    }

    @Test
    fun `compact form is smaller for long lines`() {
        val big = Line((0 until 2000).map { Position(17.0 + it * 0.0001, 51.0 + it * 0.0001) })
        assertThat(big.toGeoJsonBase64().length).isLessThan(big.toGeoJson().length)
    }

    @Test
    fun `altering after parsing works via copy`() {
        val p = geoJsonObjectFromJson(Point(1.0, 2.0).toGeoJson()) as Point
        val moved = p.copy(coordinates = Position(5.0, 6.0))
        assertThat(moved.toGeoJson()).isEqualTo("""{"type":"Point","coordinates":[5.0,6.0]}""")
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<SerializationException> { geoJsonObjectFromJson("""{"type":"Nope"}""") }
        assertFailsWith<SerializationException> { geoJsonObjectFromJson("""{"type":"Point"}""") }
        assertFailsWith<SerializationException> { geoJsonObjectFromBase64("not base64!!") }
        assertFailsWith<IllegalArgumentException> { geoJsonObjectFromJson("""{"type":"Point","coordinates":[500,0]}""") }
    }

    @Test
    fun `unsupported property type fails encoding`() {
        assertFailsWith<SerializationException> {
            Feature(Point(1.0, 2.0), properties = mapOf("x" to Any())).toGeoJson()
        }
    }
}
