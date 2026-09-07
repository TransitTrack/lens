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
 * "Rebuild" job. On a re-run the derived rows for the revision are cleared first
 * (`DerivedGtfsWriter.clearTripDerivation` / `clearExtents` here; `TripPatternProcessor` wipes the
 * six derived schedule tables itself), so it is idempotent.
 *
 * This method owns the shared [DerivationContext] open/close for the whole run: on a processor
 * failure the later processors (and their `onIngestionFailure` hooks) never run, so the `finally`
 * block here is what guarantees the context is released and a subsequent `rederive` is not blocked
 * by a stale "context already open" entry.
 *
 * When `transittrack.schedule.enabled=false` there are no post-processors and the conditional
 * `DerivationContext` / `DerivedGtfsWriter` beans are absent; this then only runs `deriveDates` —
 * matching the previous inline behaviour of `IngestionService.runPipeline`.
 *
 * Throws on failure; the caller decides how to surface it (for the import path
 * `IngestionService` funnels it through `RevisionService.fail` + `onIngestionFailure`).
 */
@Service
class DerivationService(
    postProcessors: ObjectProvider<IngestionPostProcessor>,
    contextProvider: ObjectProvider<DerivationContext>,
    derivedGtfsWriterProvider: ObjectProvider<DerivedGtfsWriter>,
    private val revisionService: RevisionService,
    private val revisions: GtfsRevisionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ordered: List<IngestionPostProcessor> =
        postProcessors.sortedWith(AnnotationAwareOrderComparator.INSTANCE)
    private val context: DerivationContext? = contextProvider.ifAvailable
    private val derivedGtfsWriter: DerivedGtfsWriter? = derivedGtfsWriterProvider.ifAvailable

    fun rederive(revisionId: Long) {
        log.info("deriving schedule model for revision {}", revisionId)

        derivedGtfsWriter?.let {
            it.clearTripDerivation(revisionId)
            it.clearExtents(revisionId)
        }

        revisionService.deriveDates(revisionId)

        try {
            for (postProcessor in ordered) {
                @Suppress("UNCHECKED_CAST")
                (postProcessor.postProcess(revisionId) as? Map<String, Long>)?.let {
                    revisionService.mergeRowCounts(revisionId, it)
                }
            }
        } catch (e: Exception) {
            ordered.forEach { runCatching { it.onIngestionFailure(revisionId) } }
            throw e
        } finally {
            context?.let { runCatching { it.close(revisionId) } }
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
