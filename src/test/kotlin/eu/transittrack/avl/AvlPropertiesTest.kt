package eu.transittrack.avl

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class AvlPropertiesTest {
    private fun bind(map: Map<String, Any>): AvlProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.avl", AvlProperties::class.java)
            .get()

    @Test
    fun `defaults`() {
        val p = bind(mapOf("transittrack.avl.enabled" to "false"))
        assertThat(p.enabled).isFalse()
        assertThat(p.match.maxDeviationM).isEqualTo(60.0)
        assertThat(p.match.scoreWeights.deviation).isEqualTo(0.4)
        assertThat(p.retention.reportHours).isEqualTo(24L)
    }
}
