package eu.transittrack.gtfs.parse

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull

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
        assertThat(GtfsFileRegistry.defs.map { it.fileName }.toSet()).isEqualTo(expected)
    }

    @Test
    fun `parsing order puts agency before routes before trips before stop_times`() {
        val order = GtfsFileRegistry.orderedForParsing.map { it.fileName }
        assertThat(order.indexOf("agency.txt")).isLessThan(order.indexOf("routes.txt"))
        assertThat(order.indexOf("routes.txt")).isLessThan(order.indexOf("trips.txt"))
        assertThat(order.indexOf("trips.txt")).isLessThan(order.indexOf("stop_times.txt"))
    }

    @Test
    fun `forFile resolves`() {
        assertThat(GtfsFileRegistry.forFile("stops.txt")).isNotNull()
        assertThat(GtfsFileRegistry.forFile("unknown.txt")).isNull()
    }
}
