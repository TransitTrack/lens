package eu.transittrack.http

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import eu.transittrack.HttpClientProperties

class HttpClientPropertiesTest {
    private fun bind(map: Map<String, Any>): HttpClientProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.http", HttpClientProperties::class.java)
            .get()

    @Test
    fun `binds the shared http client settings`() {
        val p = bind(
            mapOf(
                "transittrack.http.connect-timeout-ms" to "3000",
                "transittrack.http.read-timeout-ms" to "45000",
                "transittrack.http.max-size-bytes" to "1048576",
                "transittrack.http.user-agent" to "transittrack-test/1.0",
            ),
        )
        assertThat(p.connectTimeoutMs).isEqualTo(3000L)
        assertThat(p.readTimeoutMs).isEqualTo(45_000L)
        assertThat(p.maxSizeBytes).isEqualTo(1_048_576L)
        assertThat(p.userAgent).isEqualTo("transittrack-test/1.0")
    }

    @Test
    fun `defaults are the permissive union`() {
        val p = HttpClientProperties()
        assertThat(p.readTimeoutMs).isEqualTo(60_000L)
        assertThat(p.maxSizeBytes).isEqualTo(524_288_000L)
    }
}
