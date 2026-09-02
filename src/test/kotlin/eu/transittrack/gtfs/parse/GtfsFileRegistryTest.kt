package eu.transittrack.gtfs.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GtfsFileRegistryTest {
    @Test
    fun `registry has a def for every spec file`() {
        val expected =
            setOf(
                "agency.txt",
                "stops.txt",
                "routes.txt",
                "trips.txt",
                "stop_times.txt",
                "calendar.txt",
                "calendar_dates.txt",
                "feed_info.txt",
                "shapes.txt",
                "frequencies.txt",
                "transfers.txt",
                "fare_attributes.txt",
                "fare_rules.txt",
                "timeframes.txt",
                "rider_categories.txt",
                "fare_media.txt",
                "fare_products.txt",
                "fare_leg_rules.txt",
                "fare_leg_join_rules.txt",
                "fare_transfer_rules.txt",
                "areas.txt",
                "stop_areas.txt",
                "networks.txt",
                "route_networks.txt",
                "pathways.txt",
                "levels.txt",
                "location_groups.txt",
                "location_group_stops.txt",
                "locations.geojson",
                "booking_rules.txt",
                "translations.txt",
                "attributions.txt",
            )
        assertEquals(expected, GtfsFileRegistry.defs.map { it.fileName }.toSet())
    }

    @Test
    fun `parsing order puts agency before routes before trips before stop_times`() {
        val order = GtfsFileRegistry.orderedForParsing.map { it.fileName }
        assertTrue(order.indexOf("agency.txt") < order.indexOf("routes.txt"))
        assertTrue(order.indexOf("routes.txt") < order.indexOf("trips.txt"))
        assertTrue(order.indexOf("trips.txt") < order.indexOf("stop_times.txt"))
    }

    @Test
    fun `forFile resolves`() {
        assertNotNull(GtfsFileRegistry.forFile("stops.txt"))
        assertEquals(null, GtfsFileRegistry.forFile("unknown.txt"))
    }
}
