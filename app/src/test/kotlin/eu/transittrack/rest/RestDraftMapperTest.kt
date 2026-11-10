package eu.transittrack.rest

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

import eu.transittrack.ProjectJson
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

class RestDraftMapperTest {
    private val drafts = mock<DraftService>()
    private val feeds = mock<GtfsFeedService>()
    private val mapper = RestDraftMapper(drafts, feeds, ProjectJson)

    @Test
    fun `maps revision + lock`() {
        val rev =
            GtfsRevision(feedId = 1, status = GtfsRevisionStatus.DRAFT, sourceUrl = "u").apply {
                id = 7
                kind = DraftKind.DRAFT
                label = "P1"
                baseRevisionId = 3
                version = 5
                derivationStale = true
                createdBy = "alice"
                createdAt = Instant.parse("2026-09-06T10:00:00Z")
                rowCounts = mapOf("stops" to 10L)
                lastValidation = """{"errorCount":0,"warningCount":2}"""
            }
        val lock = DraftLock("alice", Instant.parse("2026-09-06T10:15:00Z"))
        whenever(feeds.codeOf(1)).thenReturn("stpt")
        whenever(drafts.currentLock(rev)).thenReturn(lock)
        val dto = mapper.map(rev)
        assertThat(dto.id).isEqualTo("7")
        assertThat(dto.feedCode).isEqualTo("stpt")
        assertThat(dto.baseRevisionId).isEqualTo("3")
        assertThat(dto.lock!!.editor).isEqualTo("alice")
        assertThat((dto.lastValidation as Map<*, *>)["errorCount"]).isEqualTo(0)
        assertThat((dto.rowCounts as Map<*, *>)["stops"]).isEqualTo(10L)
    }

    @Test
    fun `legacy validation stays native JSON with nested nulls and large numbers`() {
        val rev = GtfsRevision(feedId = 1, status = GtfsRevisionStatus.DRAFT, sourceUrl = "u").apply {
            id = 7
            lastValidation = """
                {
                  "publishable":false,"count":9223372036854775808,"decimal":1234567890.123456789,
                  "items":[null,0,"0",true,{"detail":null}]
                }
            """.trimIndent()
        }
        val validation = mapper.map(rev).lastValidation as Map<*, *>
        assertThat(validation["publishable"]).isEqualTo(false)
        assertThat(validation["count"]).isEqualTo(BigInteger("9223372036854775808"))
        assertThat(validation["decimal"]).isEqualTo(BigDecimal("1234567890.123456789"))
        assertThat(validation["items"]).isEqualTo(listOf(null, 0, "0", true, mapOf("detail" to null)))
    }
}
