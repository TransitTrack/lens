package eu.transittrack.schedule.optimize

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class RobustStatisticsTest {
    @Test
    fun `filters a modified-z-score outlier while preserving the robust centre`() {
        val result = RobustStatistics.filter(listOf(290.0, 300.0, 310.0, 9_999.0))

        assertThat(result.values).isEqualTo(listOf(290.0, 300.0, 310.0))
        assertThat(result.median).isEqualTo(300.0)
        assertThat(result.mad).isEqualTo(10.0)
    }

    @Test
    fun `keeps only the median when a zero MAD sample has different values`() {
        val result = RobustStatistics.filter(listOf(300.0, 300.0, 300.0, 420.0))

        assertThat(result.values).isEqualTo(listOf(300.0, 300.0, 300.0))
        assertThat(result.median).isEqualTo(300.0)
        assertThat(result.mad).isEqualTo(0.0)
    }
}
