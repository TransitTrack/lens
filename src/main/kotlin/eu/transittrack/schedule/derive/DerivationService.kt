package eu.transittrack.schedule.derive

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import org.springframework.stereotype.Service

import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.RevisionService

/**
 * Runs (or re-runs) the ordered schedule-derivation steps for one revision:
 * `RevisionService.deriveDates` followed by the ordered [IngestionPostProcessor] chain
 * (`TripPatternProcessor` -> `SchedTripProcessor` -> `TravelTimesProcessor` -> `BlockProcessor`
 * -> `GeoExtentProcessor` -> `DerivationFinalizeProcessor`).
 *
 * Used by [eu.transittrack.gtfs.ingest.IngestionService] during import and, later, by the draft
 * "Rebuild" job. The processors themselves own the shared [DerivationContext] lifecycle and
 * pre-clean the revision's derived rows (`TripPatternProcessor.postProcess` calls
 * `ScheduleWriter.deleteForRevision` first), so re-running is idempotent.
 *
 * When `transittrack.schedule.enabled=false` there are no post-processors and this only runs
 * `deriveDates` — matching the previous inline behaviour of `IngestionService.runPipeline`.
 *
 * Throws on failure; the caller decides how to surface it (for the import path
 * `IngestionService` funnels it through `RevisionService.fail` + `onIngestionFailure`).
 */
@Service
class DerivationService(
    postProcessors: ObjectProvider<IngestionPostProcessor>,
    private val revisionService: RevisionService,
    private val revisions: GtfsRevisionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ordered: List<IngestionPostProcessor> =
        postProcessors.sortedWith(AnnotationAwareOrderComparator.INSTANCE)

    fun rederive(revisionId: Long) {
        log.info("deriving schedule model for revision {}", revisionId)
        revisionService.deriveDates(revisionId)

        ordered.forEach { postProcessor ->
            @Suppress("UNCHECKED_CAST")
            (postProcessor.postProcess(revisionId) as? Map<String, Long>)?.let {
                revisionService.mergeRowCounts(revisionId, it)
            }
        }

        revisions.findById(revisionId).ifPresent {
            if (it.derivationStale || it.deriving) {
                it.derivationStale = false
                it.deriving = false
                revisions.save(it)
            }
        }
    }
}
