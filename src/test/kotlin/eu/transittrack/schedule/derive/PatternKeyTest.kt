package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo

class PatternKeyTest {
    @Test
    fun `is deterministic`() {
        val a = PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        val b = PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3", "S4"))
        assertThat(a).isEqualTo(b)
        assertThat(a.substringBeforeLast('|') + "|").isEqualTo("RA|SHP_OUT|S1_to_S4|")
    }

    @Test
    fun `same stops different shape differ`() {
        assertThat(PatternKey.of("RA", "SHP_OUT", listOf("S1", "S2", "S3")))
            .isNotEqualTo(PatternKey.of("RA", "SHP_IN", listOf("S1", "S2", "S3")))
    }

    @Test
    fun `same shape and stops on different routes differ`() {
        assertThat(PatternKey.of("RA", "SHP", listOf("S1", "S2", "S3")))
            .isNotEqualTo(PatternKey.of("RB", "SHP", listOf("S1", "S2", "S3")))
    }

    @Test
    fun `route is the first segment`() {
        assertThat(PatternKey.of("RA", "SHP", listOf("S1", "S2")).substringBefore('|')).isEqualTo("RA")
    }

    @Test
    fun `no shape uses dash`() {
        assertThat(PatternKey.of("RA", null, listOf("S1", "S2")).split('|')[1]).isEqualTo("-")
    }

    @Test
    fun `stop order matters`() {
        assertThat(PatternKey.of("RA", "SHP", listOf("S1", "S2", "S3")))
            .isNotEqualTo(PatternKey.of("RA", "SHP", listOf("S3", "S2", "S1")))
    }

    @Test
    fun `different intermediate stops differ even with same endpoints`() {
        assertThat(PatternKey.of("RA", "SHP", listOf("S1", "S2", "S4")))
            .isNotEqualTo(PatternKey.of("RA", "SHP", listOf("S1", "S3", "S4")))
    }
}
