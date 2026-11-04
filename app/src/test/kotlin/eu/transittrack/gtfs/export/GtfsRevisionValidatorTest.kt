package eu.transittrack.gtfs.export

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.store.RevisionWriter
import eu.transittrack.gtfs.support.IngestionTestFactory
import eu.transittrack.support.PostgresPerMethodTest

/**
 * `RevisionWriter` and the ingestion pieces commit on their own connections, so this must not run
 * inside a rollback transaction. Hence `NOT_SUPPORTED`.
 */
@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(IngestionTestFactory::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GtfsRevisionValidatorTest(
    @Autowired val validator: GtfsRevisionValidator,
    @Autowired val stopTimes: StopTimeRepository,
    @Autowired val revisions: GtfsRevisionRepository,
    @Autowired val writer: RevisionWriter,
    @Autowired val ingestFactory: IngestionTestFactory,
    @Autowired val json: Json,
) : PostgresPerMethodTest() {
    private var rev: Long? = null

    @Test
    fun `clean feed validates as publishable and persists the report`() {
        val (_, r) = ingestFactory.ingest("schedule-sample")
        rev = r
        val result = validator.validate(r)
        assertThat(result.publishable).isTrue()
        val persisted = revisions.findById(r)!!.lastValidation
        assertThat(persisted).isNotNull()
        val node = json.parseToJsonElement(persisted!!).jsonObject
        assertThat(node.getValue("errorCount").jsonPrimitive.int >= 0).isTrue()
        assertThat(node.getValue("publishable").jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun `a broken stop_time surfaces an error`() {
        val (_, r) = ingestFactory.ingest("schedule-sample")
        rev = r
        val anyTrip = stopTimes.findByRevisionId(r).first().tripId
        val st = stopTimes.findByTripId(r, anyTrip)
        val last = st.last()
        // last stop without times: STM_016 (CRITICAL), an R1 blocker
        last.arrivalTime = null
        last.departureTime = null
        stopTimes.save(last)
        val result = validator.validate(r)
        assertThat(result.errorCount).isGreaterThan(0)
        assertThat(result.publishable).isFalse()
        val node = json.parseToJsonElement(revisions.findById(r)!!.lastValidation!!).jsonObject
        assertThat(node.getValue("errorCount").jsonPrimitive.int).isGreaterThan(0)
        assertThat(node.getValue("publishable").jsonPrimitive.boolean).isFalse()
    }
}
