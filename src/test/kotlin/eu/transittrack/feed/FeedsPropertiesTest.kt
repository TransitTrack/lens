package eu.transittrack.feed

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.FeedsProperties

class FeedsPropertiesTest {
    private fun bind(map: Map<String, Any>): FeedsProperties =
        Binder(MapConfigurationPropertySource(map))
            .bind("transittrack.feed", FeedsProperties::class.java)
            .get()

    @Test
    fun `binds a schedule-only feed with defaults`() {
        val p = bind(
            mapOf(
                "transittrack.feed.feeds[0].code" to "wroclaw",
                "transittrack.feed.feeds[0].name" to "Wroclaw",
                "transittrack.feed.feeds[0].url" to "https://example.org/w.zip",
                "transittrack.feed.feeds[0].polling-cron" to "0 0 3 * * *",
            ),
        )
        assertThat(p.pruneConfigFeeds).isFalse()
        assertThat(p.feeds).hasSize(1)
        val f = p.feeds.single()
        assertThat(f.code).isEqualTo("wroclaw")
        assertThat(f.pollingCron).isEqualTo("0 0 3 * * *")
        assertThat(f.enabled).isTrue()
        assertThat(f.avl).isNull()
    }

    @Test
    fun `binds a feed with a nested avl block including enums and headers`() {
        val p = bind(
            mapOf(
                "transittrack.feed.prune-config-feeds" to "true",
                "transittrack.feed.feeds[0].code" to "mbta",
                "transittrack.feed.feeds[0].name" to "MBTA",
                "transittrack.feed.feeds[0].url" to "https://example.test/gtfs.zip",
                "transittrack.feed.feeds[0].avl.url" to "https://example.test/vp.pb",
                "transittrack.feed.feeds[0].avl.format" to "GTFS_RT",
                "transittrack.feed.feeds[0].avl.assignment-mode" to "DESCRIPTOR_THEN_INFER",
                "transittrack.feed.feeds[0].avl.poll-interval-sec" to "10",
                "transittrack.feed.feeds[0].avl.headers.x-api-key" to "secret",
            ),
        )
        assertThat(p.pruneConfigFeeds).isTrue()
        val avl = p.feeds.single().avl!!
        assertThat(avl.url).isEqualTo("https://example.test/vp.pb")
        assertThat(avl.format).isEqualTo(AvlFormat.GTFS_RT)
        assertThat(avl.assignmentMode).isEqualTo(AvlAssignmentMode.DESCRIPTOR_THEN_INFER)
        assertThat(avl.pollIntervalSec).isEqualTo(10)
        assertThat(avl.headers["x-api-key"]).isEqualTo("secret")
        assertThat(avl.name).isNull()
        assertThat(p.feeds.map { it.code }).containsExactly("mbta")
    }
}
