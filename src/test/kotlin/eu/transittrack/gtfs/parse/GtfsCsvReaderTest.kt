package eu.transittrack.gtfs.parse

import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GtfsCsvReaderTest {

    private fun rows(csv: String): List<GtfsRow> =
        GtfsCsvReader.read(csv.byteInputStream(Charsets.UTF_8)) { it }.toList()

    @Test
    fun `parses header-keyed values`() {
        val r = rows("a,b,c\n1,2,3\n")
        assertEquals("1", r[0].str("a"))
        assertEquals("3", r[0].str("c"))
    }

    @Test
    fun `blank and missing columns are null`() {
        val r = rows("a,b\n,x\n")
        assertNull(r[0].str("a"))
        assertNull(r[0].str("missing"))
        assertEquals("x", r[0].str("b"))
    }

    @Test
    fun `strips UTF-8 BOM from first header`() {
        val r = rows("﻿route_id,x\nR1,y\n")
        assertEquals("R1", r[0].str("route_id"))
        assertTrue("route_id" in r[0].columns)
    }

    @Test
    fun `strips UTF-8 BOM when the stream yields one byte per read`() {
        val bytes = "﻿route_id,x\nR1,y\n".toByteArray(Charsets.UTF_8)
        val oneByteAtATime = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                if (pos >= bytes.size) return -1
                b[off] = bytes[pos++]
                return 1
            }
        }
        val r = GtfsCsvReader.read(oneByteAtATime) { it }.toList()
        assertEquals("R1", r[0].str("route_id"))
        assertTrue("route_id" in r[0].columns)
    }

    @Test
    fun `handles quoted fields with commas and newlines`() {
        val r = rows("a,b\n\"x,y\",\"line1\nline2\"\n")
        assertEquals("x,y", r[0].str("a"))
        assertEquals("line1\nline2", r[0].str("b"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        val r = rows("a\n  hi  \n")
        assertEquals("hi", r[0].str("a"))
    }

    @Test
    fun `header-only file yields no rows`() {
        assertEquals(0, rows("a,b,c\n").size)
    }

    @Test
    fun `tolerates CRLF line endings`() {
        val r = rows("a,b\r\n1,2\r\n")
        assertEquals("2", r[0].str("b"))
    }
}
