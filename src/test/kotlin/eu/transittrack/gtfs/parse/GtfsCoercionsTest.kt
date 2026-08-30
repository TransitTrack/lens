package eu.transittrack.gtfs.parse

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GtfsCoercionsTest {

    private fun row(vararg pairs: Pair<String, String?>) = GtfsRow(pairs.toMap())

    @Test fun `seconds of day handles values past 24h`() {
        assertEquals(91_800, GtfsCoercions.secondsOfDay("25:30:00"))
        assertEquals(0, GtfsCoercions.secondsOfDay("00:00:00"))
        assertEquals(3_661, GtfsCoercions.secondsOfDay("1:01:01"))
    }

    @Test fun `gtfs date parses YYYYMMDD`() {
        assertEquals(LocalDate.of(2026, 3, 9), GtfsCoercions.gtfsDate("20260309"))
    }

    @Test fun `malformed values throw`() {
        assertFailsWith<GtfsParseException> { GtfsCoercions.secondsOfDay("12:xx") }
        assertFailsWith<GtfsParseException> { GtfsCoercions.gtfsDate("2026-03-09") }
    }

    @Test fun `row typed accessors`() {
        val r = row("n" to "5", "d" to "1.5", "b" to "1", "dt" to "20260101", "t" to "26:00:00", "blank" to "")
        assertEquals(5, r.int("n"))
        assertEquals(1.5, r.double("d"))
        assertEquals(true, r.bool01("b"))
        assertEquals(LocalDate.of(2026, 1, 1), r.date("dt"))
        assertEquals(93_600, r.seconds("t"))
        assertNull(r.int("blank"))
        assertNull(r.int("missing"))
    }

    @Test fun `bool01 maps 0 to false`() {
        assertEquals(false, row("b" to "0").bool01("b"))
        assertNull(row("b" to "").bool01("b"))
    }
}
