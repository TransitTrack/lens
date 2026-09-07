package eu.transittrack.gtfs.export

import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo

class GtfsFileSchemaTest {
    @Test
    fun `formats time, bool, date, null`() {
        assertThat(formatCell(21600, ColKind.TIME)).isEqualTo("6:00:00")
        assertThat(formatCell(90000, ColKind.TIME)).isEqualTo("25:00:00")
        assertThat(formatCell(true, ColKind.BOOL_INT)).isEqualTo("1")
        assertThat(formatCell(false, ColKind.BOOL_INT)).isEqualTo("0")
        assertThat(formatCell(LocalDate.of(2026, 9, 6), ColKind.DATE)).isEqualTo("20260906")
        assertThat(formatCell(null, ColKind.TEXT)).isEqualTo("")
    }

    @Test
    fun `stop_times file has spec headers and maps arrival_time`() {
        val f = GtfsFileSchema.FILES.single { it.name == "stop_times.txt" }
        assertThat(f.table).isEqualTo("stop_times")
        assertThat(f.columns.map { it.header }).containsAll("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence")
        val arr = f.columns.single { it.header == "arrival_time" }
        assertThat(arr.sqlColumn).isEqualTo("arrival_time")
        assertThat(arr.kind).isEqualTo(ColKind.TIME)
    }
}
