package eu.transittrack.schedule.derive

import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.Extent
import eu.transittrack.gtfs.model.AgencyTable
import eu.transittrack.gtfs.model.ExtentColumns
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.model.RouteTable
import eu.transittrack.gtfs.model.TripTable

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
 *
 * Every public method commits in its own transaction (`REQUIRES_NEW`).
 */
@Component
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
@Transactional
class DerivedGtfsWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyRouteExtents(
        revisionId: Long,
        byRouteId: Map<String, Extent>,
    ) = applyExtents(RouteTable, RouteTable.extent, { RouteTable.routeId eq it }, revisionId, byRouteId)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyAgencyExtents(
        revisionId: Long,
        byAgencyId: Map<String, Extent>,
    ) = applyExtents(AgencyTable, AgencyTable.extent, { AgencyTable.agencyId eq it }, revisionId, byAgencyId)

    private fun applyExtents(
        table: RevisionScopedTable<*>,
        extent: ExtentColumns,
        keyIs: (String) -> Op<Boolean>,
        revisionId: Long,
        byKey: Map<String, Extent>,
    ) {
        val entries = byKey.filterValues { !it.isEmpty }
        if (entries.isEmpty()) return
        for ((key, e) in entries) {
            table.update({ (table.revisionId eq revisionId) and keyIs(key) }) { extent.bind(it, e) }
        }
    }

    /**
     * Back-fills the derived schedule columns on `trips`, one `UPDATE` per row (as the stateless
     * session did). Each row is matched by its global PK `id`, additionally guarded by
     * `revision_id` so a stale [revisionId] can never touch another revision's rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun applyTripDerivation(
        revisionId: Long,
        updates: List<TripDerivation>,
    ) {
        if (updates.isEmpty()) return
        for (u in updates) {
            TripTable.update({ (TripTable.id eq u.tripRowId) and (TripTable.revisionId eq revisionId) }) {
                it[tripPatternId] = u.tripPatternId
                it[startTimeSec] = u.startTimeSec
                it[endTimeSec] = u.endTimeSec
                it[frequencyBased] = u.frequencyBased
                it[noSchedule] = u.noSchedule
                // coalesce(nullif(trim(trip_headsign), ''), :hs)
                it[tripHeadsign] = Coalesce(blankToNull(tripHeadsign.trim()), stringParam(u.resolvedHeadsign))
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun clearTripDerivation(revisionId: Long) {
        TripTable.update({ TripTable.revisionId eq revisionId }) {
            it[tripPatternId] = null
            it[startTimeSec] = null
            it[endTimeSec] = null
            it[frequencyBased] = null
            it[noSchedule] = null
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun clearExtents(revisionId: Long) {
        RouteTable.update({ RouteTable.revisionId eq revisionId }) { RouteTable.extent.bind(it, null) }
        AgencyTable.update({ AgencyTable.revisionId eq revisionId }) { AgencyTable.extent.bind(it, null) }
    }

    /** `nullif(value, '')`: Exposed has no `NULLIF` expression, so it is declared as a function. */
    private fun blankToNull(value: Expression<out String?>) =
        CustomFunction<String?>("NULLIF", VarCharColumnType(500), value, stringLiteral(""))
}
