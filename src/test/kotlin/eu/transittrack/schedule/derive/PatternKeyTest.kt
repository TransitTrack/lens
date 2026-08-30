package eu.transittrack.schedule.derive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PatternKeyTest {
    @Test fun `is deterministic`() {
        val a = PatternKey.of("SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        val b = PatternKey.of("SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        assertEquals(a, b)
        assertEquals("SHP_OUT|S1_to_S4|", a.substringBeforeLast('|') + "|")
    }

    @Test fun `same stops different shape differ`() {
        assertNotEquals(
            PatternKey.of("SHP_OUT", listOf("S1", "S2", "S3")),
            PatternKey.of("SHP_IN", listOf("S1", "S2", "S3")),
        )
    }

    @Test fun `no shape uses dash`() {
        assertEquals("-", PatternKey.of(null, listOf("S1", "S2")).substringBefore('|'))
    }

    @Test fun `stop order matters`() {
        assertNotEquals(
            PatternKey.of("SHP", listOf("S1", "S2", "S3")),
            PatternKey.of("SHP", listOf("S3", "S2", "S1")),
        )
    }

    @Test fun `different intermediate stops differ even with same endpoints`() {
        assertNotEquals(
            PatternKey.of("SHP", listOf("S1", "S2", "S4")),
            PatternKey.of("SHP", listOf("S1", "S3", "S4")),
        )
    }
}
