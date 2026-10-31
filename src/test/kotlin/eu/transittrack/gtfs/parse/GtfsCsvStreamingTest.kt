package eu.transittrack.gtfs.parse

import java.io.InputStream
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

class GtfsCsvStreamingTest {
    /** Produces a header plus [rows] `stop_times.txt` lines on the fly, never materialising the file. */
    private class GeneratedStopTimes(
        private val rows: Int,
    ) : InputStream() {
        private var buf = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n".toByteArray()
        private var pos = 0
        private var next = 0

        override fun read(): Int {
            if (pos == buf.size) {
                if (next == rows) return -1
                buf = "T${next / 50},08:00:00,08:00:30,S${next % 50},${next % 50}\n".toByteArray()
                pos = 0
                next++
            }
            return buf[pos++].toInt() and 0xFF
        }
    }

    @Test
    fun `streams a large file through a callback`() {
        var count = 0
        var lastLine = 0L
        GtfsCsv.forEachRecord(GeneratedStopTimes(200_000), "stop_times.txt") {
            count++
            lastLine = it.line
        }
        assertThat(count).isEqualTo(200_000)
        assertThat(lastLine).isEqualTo(200_001L)
    }
}
