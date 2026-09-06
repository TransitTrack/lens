package eu.transittrack.gtfs.draft

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class DraftPropertiesTest {
    @Test
    fun `binds editor lease`() {
        val src = MapConfigurationPropertySource(mapOf("transittrack.draft.editor-lease-minutes" to "30"))
        val p = Binder(src).bindOrCreate("transittrack.draft", DraftProperties::class.java)
        assertThat(p.editorLeaseMinutes).isEqualTo(30L)
    }

    @Test
    fun `default lease is 15`() {
        assertThat(DraftProperties().editorLeaseMinutes).isEqualTo(15L)
    }
}
