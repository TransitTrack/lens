package eu.transittrack.gtfs.parse.mapper

import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.mobilitydata.gtfsvalidator.table.GtfsAgency
import org.mobilitydata.gtfsvalidator.table.GtfsCalendar
import org.mobilitydata.gtfsvalidator.table.GtfsStopTime
import org.mobilitydata.gtfsvalidator.type.GtfsDate
import org.mobilitydata.gtfsvalidator.type.GtfsTime

class CoreMappersTest {
    @Test
    fun `maps a stop_times row with 25h arrival`() {
        val st =
            mapStopTime(
                7L,
                GtfsStopTime
                    .Builder()
                    .setTripId(
                        "T1",
                    ).setStopId(
                        "S1",
                    ).setStopSequence(
                        3,
                    ).setArrivalTime(
                        GtfsTime.fromString("25:00:00"),
                    ).setDepartureTime(GtfsTime.fromString("25:01:00"))
                    .setPickupType(1)
                    .setShapeDistTraveled(1234.5)
                    .build(),
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
        val st =
            mapStopTime(
                1L,
                GtfsStopTime
                    .Builder()
                    .setTripId("T1")
                    .setStopSequence(1)
                    .build(),
            )
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
                GtfsCalendar
                    .Builder()
                    .setServiceId(
                        "WK",
                    ).setMonday(1)
                    .setSunday(0)
                    .setStartDate(GtfsDate.fromString("20260101"))
                    .setEndDate(GtfsDate.fromString("20261231"))
                    .build(),
            )
        assertThat(c.monday).isEqualTo(true)
        assertThat(c.sunday).isEqualTo(false)
        assertThat(c.tuesday).isNull()
        assertThat(c.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(c.endDate).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `agency without agency_id maps to null rather than empty string`() {
        val a =
            mapAgency(
                1L,
                GtfsAgency
                    .Builder()
                    .setAgencyName("Metro")
                    .setAgencyUrl("https://example.test")
                    .build(),
            )
        assertThat(a.agencyId).isNull()
        assertThat(a.agencyName).isEqualTo("Metro")
    }
}
