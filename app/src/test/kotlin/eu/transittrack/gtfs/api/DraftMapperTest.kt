package eu.transittrack.gtfs.api

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

import eu.transittrack.ProjectJson
import eu.transittrack.gtfs.draft.DraftKind
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

class DraftMapperTest {
    private val mapper = DraftMapper(ProjectJson)

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
        val dto = mapper.toDto(rev, "stpt", lock)
        assertThat(dto.id).isEqualTo("7")
        assertThat(dto.feedCode).isEqualTo("stpt")
        assertThat(dto.baseRevisionId).isEqualTo("3")
        assertThat(dto.lock!!.editor).isEqualTo("alice")
        assertThat((dto.lastValidation as Map<*, *>)["errorCount"]).isEqualTo(0)
        assertThat(dto.rowCounts["stops"]).isEqualTo(10L)
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
        val validation = mapper.toDto(rev, "feed", null).lastValidation as Map<*, *>
        assertThat(validation["publishable"]).isEqualTo(false)
        assertThat(validation["count"]).isEqualTo(BigInteger("9223372036854775808"))
        assertThat(validation["decimal"]).isEqualTo(BigDecimal("1234567890.123456789"))
        assertThat(validation["items"]).isEqualTo(listOf(null, 0, "0", true, mapOf("detail" to null)))
    }
}
