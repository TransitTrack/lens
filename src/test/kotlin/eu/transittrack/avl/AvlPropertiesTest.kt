package eu.transittrack.avl

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
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

    @Test
    fun `binds a feed list with enums and headers`() {
        val p = bind(
            mapOf(
                "transittrack.avl.feeds[0].code" to "mbta-vp",
                "transittrack.avl.feeds[0].name" to "MBTA VP",
                "transittrack.avl.feeds[0].gtfs-feed-code" to "mbta",
                "transittrack.avl.feeds[0].url" to "https://example.test/vp.pb",
                "transittrack.avl.feeds[0].format" to "GTFS_RT",
                "transittrack.avl.feeds[0].assignment-mode" to "DESCRIPTOR_THEN_INFER",
                "transittrack.avl.feeds[0].poll-interval-sec" to "10",
                "transittrack.avl.feeds[0].headers.x-api-key" to "secret",
            ),
        )
        val f = p.feeds.single()
        assertThat(f.code).isEqualTo("mbta-vp")
        assertThat(f.format).isEqualTo(AvlFormat.GTFS_RT)
        assertThat(f.assignmentMode).isEqualTo(AvlAssignmentMode.DESCRIPTOR_THEN_INFER)
        assertThat(f.pollIntervalSec).isEqualTo(10)
        assertThat(f.headers["x-api-key"]).isEqualTo("secret")
        assertThat(p.feeds.map { it.code }).containsExactly("mbta-vp")
    }
}
