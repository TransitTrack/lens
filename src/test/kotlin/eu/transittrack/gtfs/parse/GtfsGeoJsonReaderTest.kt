package eu.transittrack.gtfs.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertEquals(2, locs.size)
        assertEquals("area_1", locs[0].locationId)
        assertEquals("Flex Zone", locs[0].stopName)
        assertEquals("North", locs[0].stopDesc)
        assert(locs[0].geometryJson.contains("\"Polygon\""))
        assert(locs[1].geometryJson.contains("\"MultiPolygon\""))
        assertEquals(null, locs[1].stopName)
    }

    @Test
    fun `rejects non-FeatureCollection`() {
        assertFailsWith<GtfsParseException> {
            GtfsGeoJsonReader.read("""{"type":"Feature"}""".byteInputStream())
        }
    }

    @Test
    fun `rejects feature without id`() {
        assertFailsWith<GtfsParseException> {
            GtfsGeoJsonReader.read(
                """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":null}]}""".byteInputStream(),
            )
        }
    }
}
