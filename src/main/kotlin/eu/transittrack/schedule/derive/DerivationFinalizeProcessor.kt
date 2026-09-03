package eu.transittrack.schedule.derive

import org.springframework.context.event.EventListener

import eu.transittrack.gtfs.ingest.IngestionFailedEvent
import eu.transittrack.gtfs.ingest.IngestionPostProcessor

/** Stage 6: releases the shared derivation state; carries the full-wipe safety net for late failures. */
class DerivationFinalizeProcessor(
    private val context: DerivationContext,
    private val writer: ScheduleWriter,
) : IngestionPostProcessor {
    companion object {
        const val ORDER = 60
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        context.close(revisionId)
        return emptyMap()
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { context.close(revisionId) }
    }

    @EventListener
    fun onIngestionFailed(e: IngestionFailedEvent) {
        runCatching { writer.deleteForRevision(e.revisionId) }
        runCatching { context.close(e.revisionId) }
    }
}
