package eu.transittrack.gtfs.parse

import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import tools.jackson.databind.node.ObjectNode

/**
 * The in-house reader + mappers must write exactly what the MobilityData pipeline wrote. Goldens in
 * `src/test/resources/gtfs-baseline/` were captured from that pipeline before it was removed.
 */
class BaselineEquivalenceTest {
    @Test
    fun `reader output matches the MobilityData baseline for every fixture`() {
        val goldens =
            BaselineSupport.baselineDir
                .listDirectoryEntries("*.json")
                .filter { it.name != "value-formats.json" }
        assertThat(goldens).isNotEmpty()

        val work = Files.createTempDirectory("baseline-eq-")
        try {
            goldens.forEach { golden ->
                val fixture = golden.nameWithoutExtension
                val expected = BaselineSupport.mapper.readTree(golden.toFile()) as ObjectNode
                val actual = LinkedHashMap<String, List<Map<String, String?>>>()
                GtfsZip(BaselineSupport.zipFixture(fixture, work)).use { zip ->
                    GtfsFileRegistry.orderedForParsing
                        .filter { it.kind != GtfsFileDef.Kind.GEOJSON && it.fileName in zip.fileNames }
                        .forEach { def ->
                            val rows = mutableListOf<Map<String, String?>>()
                            GtfsCsv.forEachRecord(zip.open(def.fileName)!!, def.fileName) {
                                rows += BaselineSupport.snapshot(def.map(1L, it))
                            }
                            actual[def.entityType] = rows
                        }
                }
                assertThat(BaselineSupport.mapper.valueToTree<ObjectNode>(actual), "fixture $fixture")
                    .isEqualTo(expected)
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
