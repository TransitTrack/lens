package eu.transittrack.gtfs.export

import java.nio.file.Files
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * `RevisionWriter` / the ingestion pipeline commit on their own connections, so this must not run
 * inside a rollback transaction — hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup
 * (same wiring as `DraftServiceTest`).
 *
 * Note: `GtfsFileSchema.formatCell` casts DATE cells to `java.time.LocalDate`, but
 * `ResultSet.getObject` hands back `java.sql.Date` for DATE columns — `GtfsSerializer` converts
 * in its query lambda before calling `formatCell`.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GtfsSerializerTest(
    @Autowired val serializer: GtfsSerializer,
    @Autowired val stops: StopRepository,
    @Autowired val trips: TripRepository,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
) {
    private val clean = mutableListOf<Long>()

    @AfterEach
    fun c() {
        clean.forEach {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
    }

    @Test
    fun `serialize then re-ingest yields the same core row counts`() {
        val (_, base) = ingestFactory.ingest("schedule-sample")
        clean += base
        val baseStops = stops.findByRevisionId(base).size
        val baseTrips = trips.findByRevisionId(base).size
        val baseStopTimes = stopTimes.findByRevisionId(base).size
        assertThat(baseStopTimes).isGreaterThan(0)

        val zip = Files.createTempFile("export", ".zip")
        Files.newOutputStream(zip).use { serializer.serialize(base, it) }

        val reIngested = ingestFactory.ingestFromZip(zip)
        clean += reIngested

        assertThat(stops.findByRevisionId(reIngested).size).isEqualTo(baseStops)
        assertThat(trips.findByRevisionId(reIngested).size).isEqualTo(baseTrips)
        assertThat(stopTimes.findByRevisionId(reIngested).size).isEqualTo(baseStopTimes)
    }
}
