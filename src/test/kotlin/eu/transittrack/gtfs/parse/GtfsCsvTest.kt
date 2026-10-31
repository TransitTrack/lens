package eu.transittrack.gtfs.parse

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull

class GtfsCsvTest {
    private fun records(csv: String): List<GtfsRecord> {
        val out = mutableListOf<GtfsRecord>()
        GtfsCsv.forEachRecord(csv.byteInputStream(), "test.txt") { out += it }
        return out
    }

    @Test
    fun `reads header and rows`() {
        val r = records("stop_id,stop_name\nS1,Main\n").single()
        assertThat(r.raw("stop_id")).isEqualTo("S1")
        assertThat(r.raw("stop_name")).isEqualTo("Main")
        assertThat(r.line).isEqualTo(2L)
        assertThat(r.fileName).isEqualTo("test.txt")
    }

    @Test
    fun `strips BOM and handles CRLF`() {
        val r = records("﻿stop_id,stop_name\r\nS1,Main\r\n").single()
        assertThat(r.raw("stop_id")).isEqualTo("S1")
        assertThat(r.raw("stop_name")).isEqualTo("Main")
    }

    @Test
    fun `quoted fields with comma newline and escaped quote`() {
        val rs = records("a,b\n\"x,1\",\"he said \"\"hi\"\"\nok\"\n2,3\n")
        assertThat(rs).hasSize(2)
        assertThat(rs[0].raw("a")).isEqualTo("x,1")
        assertThat(rs[0].raw("b")).isEqualTo("he said \"hi\"\nok")
        assertThat(rs[1].raw("a")).isEqualTo("2")
        assertThat(rs[1].line).isEqualTo(4L)
    }

    @Test
    fun `trims header names and ignores empty trailing column`() {
        val r = records(" stop_id , stop_name ,\nS1,Main,\n").single()
        assertThat(r.raw("stop_id")).isEqualTo("S1")
        assertThat(r.raw("stop_name")).isEqualTo("Main")
    }

    @Test
    fun `missing column, short row and blank cell read as null`() {
        val rs = records("a,b,c\n1\n2, ,3\n")
        assertThat(rs[0].raw("b")).isNull()
        assertThat(rs[0].raw("zzz")).isNull()
        assertThat(rs[1].raw("b")).isNull()
        assertThat(rs[1].raw("c")).isEqualTo("3")
    }

    @Test
    fun `skips empty lines`() {
        val rs = records("a\n1\n\n2\n")
        assertThat(rs.map { it.raw("a") }).containsExactly("1", "2")
        assertThat(rs.map { it.line }).containsExactly(2L, 4L)
    }

    @Test
    fun `last row without trailing newline is read`() {
        assertThat(records("a\n1").single().raw("a")).isEqualTo("1")
    }

    @Test
    fun `BOM before a quoted header keeps the first column`() {
        val r = records("\uFEFF\"stop_id\",\"stop_name\"\nS1,Main\n").single()
        assertThat(r.raw("stop_id")).isEqualTo("S1")
        assertThat(r.raw("stop_name")).isEqualTo("Main")
    }

    @Test
    fun `whitespace-only lines are skipped`() {
        val rs = records("a,b\n1,2\n   \n \t, \n3,4\n")
        assertThat(rs.map { it.raw("a") }).containsExactly("1", "3")
    }
}
