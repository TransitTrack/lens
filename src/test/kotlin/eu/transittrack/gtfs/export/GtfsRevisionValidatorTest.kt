package eu.transittrack.gtfs.export

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory

/**
 * `RevisionWriter` and the ingestion pieces commit on their own connections, so this must not run
 * inside a rollback transaction. Hence `NOT_SUPPORTED` plus explicit `@AfterEach` cleanup.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class, IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GtfsRevisionValidatorTest(
    @Autowired val validator: GtfsRevisionValidator,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: JsonMapper,
) {
    private var rev: Long? = null

    @AfterEach
    fun cleanup() {
        rev?.let {
            runCatching { writer.deleteAllForRevision(it) }
            runCatching { revisions.deleteById(it) }
        }
    }

    @Test
    fun `clean feed validates with no errors and persists the report`() {
        val (_, r) = ingestFactory.ingest("schedule-sample")
        rev = r
        val result = validator.validate(r)
        assertThat(result.errorCount).isEqualTo(0)
        val persisted = revisions.findById(r).get().lastValidation
        assertThat(persisted).isNotNull()
        val node = json.readTree(persisted!!)
        assertThat(node.path("errorCount").isNumber).isTrue()
        assertThat(node.path("errorCount").asInt(-1)).isEqualTo(0)
    }

    @Test
    fun `a broken stop_time surfaces an error`() {
        val (_, r) = ingestFactory.ingest("schedule-sample")
        rev = r
        val anyTrip = stopTimes.findByRevisionId(r).first().tripId
        val st = stopTimes.findByTripId(r, anyTrip)
        val last = st.last()
        last.arrivalTime = 0
        last.departureTime = 0
        stopTimes.save(last)
        val result = validator.validate(r)
        assertThat(result.errorCount).isGreaterThan(0)
        val node = json.readTree(revisions.findById(r).get().lastValidation!!)
        assertThat(node.path("errorCount").isNumber).isTrue()
        assertThat(node.path("errorCount").asInt(-1)).isGreaterThan(0)
    }
}
