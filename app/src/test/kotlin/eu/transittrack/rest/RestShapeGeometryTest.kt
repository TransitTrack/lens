package eu.transittrack.rest

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

import eu.transittrack.geo.Line
import eu.transittrack.geo.Position
import eu.transittrack.geo.json.geoJsonObjectFromBase64
import eu.transittrack.gtfs.model.Shape
import eu.transittrack.gtfs.model.ShapePoint

class RestShapeGeometryTest {
    private val mapper = testEntityMapper()

    private fun shape(
        vararg points: ShapePoint,
        stored: String? = null,
    ) = mapper.shape(Shape(1, "SHP", points.size, null, stored), points.toList())

    private fun pt(
        seq: Int,
        lat: Double?,
        lon: Double?,
    ) = ShapePoint(1, "SHP", lat, lon, seq, null)

    @Test
    fun `geometry is a base64 gzip LineString in sequence order`() {
        val encoded = shape(pt(2, 51.2, 17.2), pt(1, 51.1, 17.1), pt(3, 51.3, 17.3)).geometry!!

        assertThat(geoJsonObjectFromBase64(encoded))
            .isEqualTo(Line(listOf(Position(17.1, 51.1), Position(17.2, 51.2), Position(17.3, 51.3))))
    }

    @Test
    fun `points without valid coordinates are skipped`() {
        val encoded = shape(pt(1, 51.1, 17.1), pt(2, null, 17.2), pt(3, 99.0, 17.3), pt(4, 51.4, 17.4)).geometry!!

        assertThat(geoJsonObjectFromBase64(encoded))
            .isEqualTo(Line(listOf(Position(17.1, 51.1), Position(17.4, 51.4))))
    }

    @Test
    fun `a stored geometry is returned as-is without touching the points`() {
        assertThat(shape(stored = "stored").geometry).isEqualTo("stored")
    }

    @Test
    fun `fewer than two valid points gives no geometry`() {
        assertThat(shape(pt(1, 51.1, 17.1)).geometry).isNull()
        assertThat(shape().geometry).isNull()
    }
}
