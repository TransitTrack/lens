package eu.transittrack.schedule.derive

import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.Extent

/**
 * Writes derivation results back onto raw GTFS tables — route / agency extents and the
 * `trips.trip_pattern_id` link. Kept separate from [ScheduleWriter], which owns the derived tables
 * and whose `deleteForRevision` must not touch GTFS tables.
 */
@Component
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class DerivedGtfsWriter(
    emf: EntityManagerFactory,
) {
    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    fun applyRouteExtents(
        revisionId: Long,
        byRouteId: Map<String, Extent>,
    ) = applyExtents("routes", "route_id", revisionId, byRouteId)

    fun applyAgencyExtents(
        revisionId: Long,
        byAgencyId: Map<String, Extent>,
    ) = applyExtents("agencies", "agency_id", revisionId, byAgencyId)

    private fun applyExtents(
        table: String,
        keyCol: String,
        revisionId: Long,
        byKey: Map<String, Extent>,
    ) {
        val entries = byKey.filterValues { !it.isEmpty }
        if (entries.isEmpty()) return
        sessionFactory
            .inStatelessTransaction { session ->
                for ((key, e) in entries) {
                    session
                        .createNativeMutationQuery(
                            """update $table set min_lat = :mnla, min_lon = :mnlo, max_lat = :mxla, max_lon = :mxlo where revision_id = :r and $keyCol = :k""",
                        ).setParameter("mnla", e.minLat)
                        .setParameter("mnlo", e.minLon)
                        .setParameter("mxla", e.maxLat)
                        .setParameter("mxlo", e.maxLon)
                        .setParameter("r", revisionId)
                        .setParameter("k", key)
                        .executeUpdate()
                }
            }
    }

    /** [links] maps `trips.id` -> `trip_patterns.id`. */
    fun applyTripPatternLinks(links: Map<Long, Long>) {
        if (links.isEmpty()) return
        sessionFactory
            .inStatelessTransaction { session ->
                for ((tripId, patternId) in links) {
                    session
                        .createNativeMutationQuery("update trips set trip_pattern_id = :pid where id = :tid")
                        .setParameter("pid", patternId)
                        .setParameter("tid", tripId)
                        .executeUpdate()
                }
            }
    }
}
