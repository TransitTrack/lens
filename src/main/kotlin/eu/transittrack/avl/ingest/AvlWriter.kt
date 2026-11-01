package eu.transittrack.avl.ingest

import java.time.Instant

import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.AvlReportTable
import eu.transittrack.avl.model.VehicleMatch
import eu.transittrack.avl.model.VehicleMatchTable
import eu.transittrack.avl.model.VehicleStateTable
import eu.transittrack.db.batchInsertWithoutResults
import eu.transittrack.db.batchUpsertWithoutResults

data class VehicleStateUpsert(
    val feedCode: String,
    val vehicleId: String,
    val vehicleLabel: String?,
    val reportTs: Instant,
    val lat: Double,
    val lon: Double,
    val bearing: Double?,
    val speedMps: Double?,
    val occupancyStatus: Int?,
    val matched: Boolean,
    val stale: Boolean,
    val consecutiveFailures: Int,
    val revisionId: Long?,
    val tripRowId: Long?,
    val blockPk: Long?,
    val tripPatternId: Long?,
    val stopPathIndex: Int?,
    val distanceAlongTripM: Double?,
    val scheduleAdherenceSec: Int?,
    val snappedLat: Double?,
    val snappedLon: Double?,
    val updatedAt: Instant,
)

/**
 * Bulk persistence for the AVL pipeline: JDBC-batched inserts of `avl_report` and `vehicle_match`
 * rows (their generated ids are never read back), and an `INSERT ... ON CONFLICT (feed_code,
 * vehicle_id) DO UPDATE` upsert of `vehicle_state` that updates every column except the key and `id`.
 *
 * Every method commits in its own transaction (`REQUIRES_NEW`): reports written by
 * [AvlIngestService.pollOnce] stand even if its poll transaction
 * rolls back afterwards.
 */
@Component
@Transactional
class AvlWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertReports(rows: List<AvlReportRow>) {
        if (rows.isEmpty()) return
        AvlReportTable.batchInsertWithoutResults(rows) { AvlReportTable.bind(this, it) }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun insertMatches(matches: List<VehicleMatch>) {
        if (matches.isEmpty()) return
        VehicleMatchTable.batchInsertWithoutResults(matches) { VehicleMatchTable.bind(this, it) }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun upsertState(s: VehicleStateUpsert) {
        VehicleStateTable.upsert(VehicleStateTable.feedCode, VehicleStateTable.vehicleId) { bind(it, s) }
    }

    /**
     * Applied in list order (one statement per row in a JDBC batch), so a later upsert for the same vehicle wins.
     * Last-wins for a key repeated within one batch relies on pgjdbc sending separate statements (`reWriteBatchedInserts` off).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun upsertStates(states: List<VehicleStateUpsert>) {
        if (states.isEmpty()) return
        VehicleStateTable.batchUpsertWithoutResults(states, VehicleStateTable.feedCode, VehicleStateTable.vehicleId) { bind(this, it) }
    }

    /** Every column but `id`, which the insert takes from `vehicle_state_seq` and an update leaves alone. */
    private fun bind(
        st: UpdateBuilder<*>,
        s: VehicleStateUpsert,
    ) = with(VehicleStateTable) {
        st[feedCode] = s.feedCode
        st[vehicleId] = s.vehicleId
        st[vehicleLabel] = s.vehicleLabel
        st[reportTs] = s.reportTs
        st[lat] = s.lat
        st[lon] = s.lon
        st[bearing] = s.bearing
        st[speedMps] = s.speedMps
        st[occupancyStatus] = s.occupancyStatus?.toShort()
        st[matched] = s.matched
        st[stale] = s.stale
        st[consecutiveFailures] = s.consecutiveFailures
        st[revisionId] = s.revisionId
        st[tripRowId] = s.tripRowId
        st[blockPk] = s.blockPk
        st[tripPatternId] = s.tripPatternId
        st[stopPathIndex] = s.stopPathIndex
        st[distanceAlongTripM] = s.distanceAlongTripM
        st[scheduleAdherenceSec] = s.scheduleAdherenceSec
        st[snappedLat] = s.snappedLat
        st[snappedLon] = s.snappedLon
        st[updatedAt] = s.updatedAt
    }
}
