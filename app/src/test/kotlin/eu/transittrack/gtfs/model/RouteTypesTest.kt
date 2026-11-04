package eu.transittrack.gtfs.model

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class RouteTypesTest {
    private fun assertBasic(
        expected: Int,
        vararg routeTypes: Int,
    ) {
        for (routeType in routeTypes) {
            assertThat(RouteTypes.basic(routeType), "basic($routeType)").isEqualTo(expected)
        }
    }

    @Test
    fun `basic route types map to themselves`() {
        for (routeType in listOf(0, 1, 2, 3, 4, 5, 6, 7, 11, 12)) {
            assertThat(RouteTypes.basic(routeType), "basic($routeType)").isEqualTo(routeType)
        }
    }

    @Test
    fun `railway and suburban railway services are rail`() = assertBasic(RouteTypes.RAIL, 100, 109, 199, 300, 399)

    @Test
    fun `coach and bus services are bus`() = assertBasic(RouteTypes.BUS, 200, 299, 700, 704, 799)

    @Test
    fun `urban railway metro and underground services are subway`() = assertBasic(RouteTypes.SUBWAY, 400, 401, 402, 404, 500, 600)

    @Test
    fun `urban monorail service is monorail`() = assertBasic(RouteTypes.MONORAIL, 405)

    @Test
    fun `trolleybus services are trolleybus`() = assertBasic(RouteTypes.TROLLEYBUS, 800, 899)

    @Test
    fun `tram services are tram`() = assertBasic(RouteTypes.TRAM, 900, 906, 999)

    @Test
    fun `water transport and ferry services are ferry`() = assertBasic(RouteTypes.FERRY, 1000, 1099, 1200)

    @Test
    fun `aerial lift services are aerial lift`() = assertBasic(RouteTypes.AERIAL_LIFT, 1300, 1301, 1399)

    @Test
    fun `funicular service is funicular`() = assertBasic(RouteTypes.FUNICULAR, 1400)

    @Test
    fun `extended types without a basic equivalent are kept`() {
        // Air, taxi and miscellaneous services have no basic GTFS type.
        for (routeType in listOf(1100, 1500, 1501, 1700, 1702)) {
            assertThat(RouteTypes.basic(routeType), "basic($routeType)").isEqualTo(routeType)
        }
    }
}
