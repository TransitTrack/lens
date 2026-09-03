package eu.transittrack.gtfs.parse

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull

import eu.transittrack.exception.ParseException

class GtfsGeoJsonReaderTest {
    private val fc =
        """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","id":"area_1",
           "properties":{"stop_name":"Flex Zone","stop_desc":"North"},
           "geometry":{"type":"Polygon","coordinates":[[[17.0,51.0],[17.1,51.0],[17.1,51.1],[17.0,51.0]]]}},
          {"type":"Feature","id":"area_2","properties":{},
           "geometry":{"type":"MultiPolygon","coordinates":[[[[1.0,2.0],[3.0,4.0],[5.0,6.0],[1.0,2.0]]]]}}
        ]}
        """.trimIndent()

    @Test
    fun `reads features`() {
        val locs = GtfsGeoJsonReader.read(fc.byteInputStream())
        assertThat(locs).hasSize(2)
        assertThat(locs[0].locationId).isEqualTo("area_1")
        assertThat(locs[0].stopName).isEqualTo("Flex Zone")
        assertThat(locs[0].stopDesc).isEqualTo("North")
        assertThat(locs[0].geometryJson).contains("\"Polygon\"")
        assertThat(locs[1].geometryJson).contains("\"MultiPolygon\"")
        assertThat(locs[1].stopName).isNull()
    }

    @Test
    fun `rejects non-FeatureCollection`() {
        assertFailure {
            GtfsGeoJsonReader.read("""{"type":"Feature"}""".byteInputStream())
        }.isInstanceOf<ParseException>()
    }

    @Test
    fun `rejects feature without id`() {
        assertFailure {
            GtfsGeoJsonReader.read(
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":null}]}""".byteInputStream(),
            )
        }.isInstanceOf<ParseException>()
    }
}
