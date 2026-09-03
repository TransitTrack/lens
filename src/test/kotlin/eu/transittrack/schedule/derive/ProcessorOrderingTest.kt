package eu.transittrack.schedule.derive

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.containsExactly
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AnnotationAwareOrderComparator

import eu.transittrack.TestcontainersConfiguration
import eu.transittrack.gtfs.ingest.IngestionPostProcessor

@SpringBootTest(classes = [eu.transittrack.Application::class])
@Import(TestcontainersConfiguration::class)
class ProcessorOrderingTest(
    @Autowired val processors: List<IngestionPostProcessor>,
) {
    @Test
    fun `schedule processors run in stage order`() {
        val names =
            processors
                .sortedWith(AnnotationAwareOrderComparator.INSTANCE)
                .map { it.javaClass.simpleName }
                .filter { it in SCHEDULE }
        assertThat(names).containsExactly(
            "TripPatternProcessor",
            "SchedTripProcessor",
            "TravelTimesProcessor",
            "BlockProcessor",
            "GeoExtentProcessor",
            "DerivationFinalizeProcessor",
        )
    }

    companion object {
        val SCHEDULE =
            setOf(
                "TripPatternProcessor",
                "SchedTripProcessor",
                "TravelTimesProcessor",
                "BlockProcessor",
                "GeoExtentProcessor",
                "DerivationFinalizeProcessor",
            )
    }
}
