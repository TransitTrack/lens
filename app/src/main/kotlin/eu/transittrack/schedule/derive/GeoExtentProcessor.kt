package eu.transittrack.schedule.derive

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

import eu.transittrack.Extent
import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.model.RouteRepository

/**
 * Stage 5 of schedule derivation. Writes route extents (unions of their pattern extents, computed by
 * stage 1) onto the raw `routes` table, then derives per-agency extents (union of each agency's
 * routes) and writes them onto the raw `agencies` table.
 */
@Component
@Order(GeoExtentProcessor.ORDER)
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class GeoExtentProcessor(
    private val context: DerivationContext,
    private val derivedGtfsWriter: DerivedGtfsWriter,
    private val routes: RouteRepository,
) : IngestionPostProcessor {
    companion object {
        const val ORDER = 50
    }

    override fun postProcess(revisionId: Long): Map<String, Long> {
        val state = context.get(revisionId)
        derivedGtfsWriter.applyRouteExtents(revisionId, state.patternExtents)
        val agencyExtents = HashMap<String, Extent>()
        for (route in routes.findByRevisionId(revisionId)) {
            val re = state.patternExtents[route.routeId] ?: continue
            val aid = route.agencyId ?: continue
            agencyExtents.getOrPut(aid) { Extent() }.add(re)
        }
        derivedGtfsWriter.applyAgencyExtents(revisionId, agencyExtents)
        return emptyMap()
    }

    override fun onIngestionFailure(revisionId: Long) {
        runCatching { derivedGtfsWriter.clearExtents(revisionId) }
        runCatching { context.close(revisionId) }
    }
}
