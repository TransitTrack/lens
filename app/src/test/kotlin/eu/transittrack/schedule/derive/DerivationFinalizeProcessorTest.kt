package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.mockito.kotlin.mock

class DerivationFinalizeProcessorTest {
    private val ctx = DerivationContext()
    private val p = DerivationFinalizeProcessor(ctx, mock())

    @Test
    fun `postProcess closes the context`() {
        ctx.open(5L)
        p.postProcess(5L)
        assertThat(ctx.size()).isEqualTo(0)
    }

    @Test
    fun `onIngestionFailure closes the context`() {
        ctx.open(9L)
        p.onIngestionFailure(9L)
        assertThat(ctx.size()).isEqualTo(0)
    }
}
