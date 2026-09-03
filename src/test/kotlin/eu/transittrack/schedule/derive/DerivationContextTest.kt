package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf

class DerivationContextTest {
    private val ctx = DerivationContext()

    @Test
    fun `open then get returns the same state, close removes it`() {
        val s = ctx.open(7L)
        s.patternIdByKey["k"] = 1L
        assertThat(ctx.get(7L).patternIdByKey["k"]).isEqualTo(1L)
        assertThat(ctx.size()).isEqualTo(1)
        ctx.close(7L)
        assertThat(ctx.size()).isEqualTo(0)
    }

    @Test
    fun `double open throws`() {
        ctx.open(1L)
        assertFailure { ctx.open(1L) }.isInstanceOf<IllegalStateException>()
    }

    @Test
    fun `get before open throws`() {
        assertFailure { ctx.get(99L) }.isInstanceOf<IllegalStateException>()
    }

    @Test
    fun `close is idempotent`() {
        ctx.close(123L)
        ctx.close(123L)
    }
}
