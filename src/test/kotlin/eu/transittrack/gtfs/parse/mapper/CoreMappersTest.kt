package eu.transittrack.gtfs.parse.mapper

import eu.transittrack.gtfs.parse.GtfsRow
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreMappersTest {
    private fun row(vararg p: Pair<String, String?>) = GtfsRow(p.toMap())

    @Test fun `maps a stop_times row with 25h arrival`() {
        val st = mapStopTime(7L, row(
            "trip_id" to "T1", "stop_id" to "S1", "stop_sequence" to "3",
            "arrival_time" to "25:00:00", "departure_time" to "25:01:00",
            "pickup_type" to "1", "shape_dist_traveled" to "1234.5",
        ))
        assertEquals(7L, st.revisionId)
        assertEquals("T1", st.tripId)
        assertEquals(3, st.stopSequence)
        assertEquals(90_000, st.arrivalTime)
        assertEquals(1, st.pickupType)
        assertEquals(1234.5, st.shapeDistTraveled)
    }

    @Test fun `maps a calendar row with 0-1 day flags`() {
        val c = mapCalendar(1L, row(
            "service_id" to "WK", "monday" to "1", "sunday" to "0",
            "start_date" to "20260101", "end_date" to "20261231",
        ))
        assertEquals(true, c.monday)
        assertEquals(false, c.sunday)
        assertEquals(java.time.LocalDate.of(2026,1,1), c.startDate)
    }
}
