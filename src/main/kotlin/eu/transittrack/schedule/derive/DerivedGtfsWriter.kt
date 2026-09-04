package eu.transittrack.schedule.derive

import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.Extent

data class TripDerivation(
    val tripRowId: Long,
    val tripPatternId: Long,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val frequencyBased: Boolean,
    val noSchedule: Boolean,
    val resolvedHeadsign: String,
)

/**
 * Writes derivation results back onto raw GTFS tables:
 *  - route / agency extents (`applyRouteExtents` / `applyAgencyExtents`, undone by `clearExtents`);
 *  - the five derived schedule columns on `trips` (`trip_pattern_id`, `start_time_sec`,
 *    `end_time_sec`, `frequency_based`, `no_schedule`) plus a blank `trip_headsign` filled from the
 *    resolved headsign (`applyTripDerivation`, undone by `clearTripDerivation`).
 *
 * Kept separate from [ScheduleWriter], which owns the derived tables and whose `deleteForRevision`
 * must not touch GTFS tables.
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

    /**
     * Back-fills the derived schedule columns on `trips`. Runs one `UPDATE` per row within a single
     * stateless-session transaction (Hibernate does not batch native mutation queries — same idiom
     * as [applyRouteExtents]). Each row is matched by its global PK `id`, additionally guarded by
     * `revision_id` so a stale [revisionId] can never touch another revision's rows.
     */
    fun applyTripDerivation(
        revisionId: Long,
        updates: List<TripDerivation>,
    ) {
        if (updates.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (u in updates) {
                session
                    .createNativeMutationQuery(
                        """
                        update trips
                           set trip_pattern_id = :tpid, start_time_sec = :st, end_time_sec = :et,
                               frequency_based = :fb, no_schedule = :ns,
                               trip_headsign = coalesce(nullif(trim(trip_headsign), ''), :hs)
                         where id = :id and revision_id = :r
                        """.trimIndent(),
                    ).setParameter("r", revisionId)
                    .setParameter("tpid", u.tripPatternId)
                    .setParameter("st", u.startTimeSec)
                    .setParameter("et", u.endTimeSec)
                    .setParameter("fb", u.frequencyBased)
                    .setParameter("ns", u.noSchedule)
                    .setParameter("hs", u.resolvedHeadsign)
                    .setParameter("id", u.tripRowId)
                    .executeUpdate()
            }
        }
    }

    fun clearTripDerivation(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery(
                    """
                    update trips set trip_pattern_id = null, start_time_sec = null, end_time_sec = null,
                           frequency_based = null, no_schedule = null
                     where revision_id = :r
                    """.trimIndent(),
                ).setParameter("r", revisionId)
                .executeUpdate()
        }
    }

    fun clearExtents(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            for (t in listOf("routes", "agencies")) {
                session
                    .createNativeMutationQuery(
                        "update $t set min_lat = null, min_lon = null, max_lat = null, max_lon = null where revision_id = :r",
                    ).setParameter("r", revisionId)
                    .executeUpdate()
            }
        }
    }
}
