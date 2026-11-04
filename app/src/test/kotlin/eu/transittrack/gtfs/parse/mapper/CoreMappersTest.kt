package eu.transittrack.gtfs.parse.mapper

import java.time.LocalDate
import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull

import eu.transittrack.gtfs.parse.BaselineSupport.record
import eu.transittrack.gtfs.parse.MissingRequiredFieldException
import eu.transittrack.gtfs.parse.row.AgencyRow
import eu.transittrack.gtfs.parse.row.CalendarRow
import eu.transittrack.gtfs.parse.row.RouteRow
import eu.transittrack.gtfs.parse.row.StopTimeRow
import eu.transittrack.gtfs.parse.row.TripRow

class CoreMappersTest {
    @Test
    fun `maps a stop_times row with 25h arrival`() {
        val st =
            mapStopTime(
                7L,
                StopTimeRow.from(
                    record(
                        "trip_id,stop_id,stop_sequence,arrival_time,departure_time,pickup_type,shape_dist_traveled",
                        "T1,S1,3,25:00:00,25:01:00,1,1234.5",
                    ),
                ),
            )
        assertThat(st.revisionId).isEqualTo(7L)
        assertThat(st.tripId).isEqualTo("T1")
        assertThat(st.stopSequence).isEqualTo(3)
        assertThat(st.arrivalTime).isEqualTo(90_000)
        assertThat(st.pickupType).isEqualTo(1)
        assertThat(st.shapeDistTraveled).isEqualTo(1234.5)
    }

    @Test
    fun `absent optional stop_time fields map to null`() {
        val st = mapStopTime(1L, StopTimeRow.from(record("trip_id,stop_sequence", "T1,1")))
        assertThat(st.arrivalTime).isNull()
        assertThat(st.departureTime).isNull()
        assertThat(st.stopId).isNull()
        assertThat(st.pickupType).isNull()
        assertThat(st.shapeDistTraveled).isNull()
    }

    @Test
    fun `maps a calendar row with 0-1 day flags`() {
        val c =
            mapCalendar(
                1L,
                CalendarRow.from(
                    record("service_id,monday,sunday,start_date,end_date", "WK,1,0,20260101,20261231"),
                ),
            )
        assertThat(c.monday).isEqualTo(true)
        assertThat(c.sunday).isEqualTo(false)
        assertThat(c.tuesday).isNull()
        assertThat(c.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(c.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `agency without agency_id maps to null rather than empty string`() {
        val a = mapAgency(1L, AgencyRow.from(record("agency_name,agency_url", "Metro,https://example.test")))
        assertThat(a.agencyId).isNull()
        assertThat(a.agencyName).isEqualTo("Metro")
    }

    @Test
    fun `extended route_type is kept raw`() {
        val r = mapRoute(1L, RouteRow.from(record("route_id,route_type,route_color", "R1,704,ff00aa")))
        assertThat(r.routeType).isEqualTo(704)
        assertThat(r.routeColor).isEqualTo("#FF00AA")
    }

    @Test
    fun `missing trip_id throws MissingRequiredFieldException`() {
        val row = TripRow.from(record("route_id,service_id,trip_id", "R1,WK,", "trips.txt"))
        assertFailure { mapTrip(1L, row) }
            .isInstanceOf<MissingRequiredFieldException>()
            .given {
                assertThat(it.column).isEqualTo("trip_id")
                assertThat(it.line).isEqualTo(2L)
                assertThat(it.fileName).isEqualTo("trips.txt")
            }
    }
}
