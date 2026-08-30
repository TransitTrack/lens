package eu.transittrack.schedule.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PatternKeyTest {
    @Test fun `is deterministic`() {
        val a = PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        val b = PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        assertEquals(a, b)
        assertEquals("RA|SHP_OUT|S1_to_S4|", a.substringBeforeLast('|') + "|")
    }

    @Test fun `same stops different shape differ`() {
        assertNotEquals(
            PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3")),
            PatternKey.of("RA", "SHP_IN", listOf("S1", "S2", "S3")),
        )
    }

    @Test fun `same shape and stops on different routes differ`() {
        assertNotEquals(
            PatternKey.of("RA", "SHP", listOf("S1", "S2", "S3")),
            PatternKey.of("RB", "SHP", listOf("S1", "S2", "S3")),
        )
    }

    @Test fun `route is the first segment`() {
        assertEquals("RA", PatternKey.of("RA", "SHP", listOf("S1", "S2")).substringBefore('|'))
    }

    @Test fun `no shape uses dash`() {
        assertEquals("-", PatternKey.of("RA", null, listOf("S1", "S2")).split('|')[1])
    }

    @Test fun `stop order matters`() {
        assertNotEquals(
            PatternKey.of("RA", "SHP", listOf("S1", "S2", "S3")),
            PatternKey.of("RA", "SHP", listOf("S3", "S2", "S1")),
        )
    }

    @Test fun `different intermediate stops differ even with same endpoints`() {
        assertNotEquals(
            PatternKey.of("RA", "SHP", listOf("S1", "S2", "S4")),
            PatternKey.of("RA", "SHP", listOf("S1", "S3", "S4")),
        )
    }
}
