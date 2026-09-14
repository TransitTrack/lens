package eu.transittrack.avl.model

import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.springframework.beans.factory.annotation.Autowired

import eu.transittrack.AvlAssignmentMode
import eu.transittrack.AvlFormat
import eu.transittrack.gtfs.support.PostgresSliceTest
import eu.transittrack.support.PostgresPerMethodTest

@PostgresSliceTest
class AvlFeedEntityTest(
    @Autowired val repo: AvlFeedRepository,
) : PostgresPerMethodTest() {
    @Test
    fun `round-trips including enum ordinals and jsonb headers`() {
        val now = Instant.parse("2026-09-04T10:00:00Z")
        repo.save(
            AvlFeed(
                code = "f1",
                name = "Feed 1",
                gtfsFeedCode = "g1",
                url = "https://x.test/vp.pb",
                format = AvlFormat.GTFS_RT,
                pollIntervalSec = 15,
                assignmentMode = AvlAssignmentMode.FULL_INFERENCE,
                enabled = true,
                headers = mapOf("k" to "v"),
                source = AvlFeedSourceKind.CONFIG,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val f = repo.findByCode("f1")
        assertThat(f).isNotNull()
        assertThat(f!!.assignmentMode).isEqualTo(AvlAssignmentMode.FULL_INFERENCE)
        assertThat(f.headers).isEqualTo(mapOf("k" to "v"))
        assertThat(f.id).isNotNull()
    }
}
