package eu.transittrack.gtfs.export

import java.time.LocalDate
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsAtLeast
import assertk.assertions.isEqualTo

import eu.transittrack.gtfs.draft.DraftRawTables

class GtfsFileSchemaTest {
    @Test
    fun `every forked raw table is serialized`() {
        val covered = GtfsFileSchema.FILES.map { it.table }.toSet()
        // `locations` = GTFS-Flex locations.geojson (raw GeoJSON text, not a CSV).
        // `shapes` = TransitTrack's per-revision shape summary (point_count / length_m); it has no
        // GTFS spec file and is rebuilt from shapes.txt on ingest.
        val knownDeferred = setOf("locations", "shapes")
        assertThat(DraftRawTables.NAMES.toSet() - covered - knownDeferred).isEqualTo(emptySet<String>())
    }

    @Test
    fun `formats time, bool, date, null`() {
        assertThat(formatCell(21600, ColKind.TIME)).isEqualTo("06:00:00")
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
        assertThat(f.columns.map { it.header }).containsAtLeast("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence")
        val arr = f.columns.single { it.header == "arrival_time" }
        assertThat(arr.sqlColumn).isEqualTo("arrival_time")
        assertThat(arr.kind).isEqualTo(ColKind.TIME)
    }
}
