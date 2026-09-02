package eu.transittrack.gtfs.parse.mapper

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(7L, st.revisionId)
        assertEquals("T1", st.tripId)
        assertEquals(3, st.stopSequence)
        assertEquals(90_000, st.arrivalTime)
        assertEquals(1, st.pickupType)
        assertEquals(1234.5, st.shapeDistTraveled)
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
        assertNull(st.arrivalTime)
        assertNull(st.departureTime)
        assertNull(st.stopId)
        assertNull(st.pickupType)
        assertNull(st.shapeDistTraveled)
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
        assertEquals(true, c.monday)
        assertEquals(false, c.sunday)
        assertNull(c.tuesday)
        assertEquals(LocalDate.of(2026, 1, 1), c.startDate)
        assertEquals(LocalDate.of(2026, 12, 31), c.endDate)
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
        assertNull(a.agencyId)
        assertEquals("Metro", a.agencyName)
    }
}
