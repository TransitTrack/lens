package eu.transittrack.avl.ingest

import java.time.Instant
import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.springframework.stereotype.Component

import eu.transittrack.avl.model.AvlReportRow
import eu.transittrack.avl.model.VehicleMatch

data class VehicleStateUpsert(
    val feedId: Long,
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
 * Bulk persistence for the AVL pipeline. `avl_report` and `vehicle_match` go in via a Hibernate
 * [org.hibernate.StatelessSession] (JDBC-batched, no persistence context); `vehicle_state` is a
 * native `INSERT ... ON CONFLICT` upsert keyed on `(feed_id, vehicle_id)`. The `vehicle_state.id`
 * column is a Postgres IDENTITY, so it is left out of the insert entirely.
 */
@Component
class AvlWriter(
    emf: EntityManagerFactory,
) {
    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    fun insertReports(rows: List<AvlReportRow>) {
        if (rows.isEmpty()) return
        sessionFactory.inStatelessTransaction { session -> rows.forEach(session::insert) }
    }

    fun insertMatches(matches: List<VehicleMatch>) {
        if (matches.isEmpty()) return
        sessionFactory.inStatelessTransaction { session -> matches.forEach(session::insert) }
    }

    fun upsertState(s: VehicleStateUpsert) {
        sessionFactory.inStatelessTransaction { session -> upsertState(session, s) }
    }

    fun upsertStates(states: List<VehicleStateUpsert>) {
        if (states.isEmpty()) return
        sessionFactory.inStatelessTransaction { session -> states.forEach { upsertState(session, it) } }
    }

    private fun upsertState(
        session: StatelessSession,
        s: VehicleStateUpsert,
    ) {
        session
            .createNativeMutationQuery(UPSERT_SQL)
            .setParameter("feed_id", s.feedId)
            .setParameter("vehicle_id", s.vehicleId)
            .setParameter("vehicle_label", s.vehicleLabel)
            .setParameter("report_ts", s.reportTs)
            .setParameter("lat", s.lat)
            .setParameter("lon", s.lon)
            .setParameter("bearing", s.bearing)
            .setParameter("speed_mps", s.speedMps)
            .setParameter("occupancy_status", s.occupancyStatus)
            .setParameter("matched", s.matched)
            .setParameter("stale", s.stale)
            .setParameter("consecutive_failures", s.consecutiveFailures)
            .setParameter("revision_id", s.revisionId)
            .setParameter("trip_row_id", s.tripRowId)
            .setParameter("block_pk", s.blockPk)
            .setParameter("trip_pattern_id", s.tripPatternId)
            .setParameter("stop_path_index", s.stopPathIndex)
            .setParameter("distance_along_trip_m", s.distanceAlongTripM)
            .setParameter("schedule_adherence_sec", s.scheduleAdherenceSec)
            .setParameter("snapped_lat", s.snappedLat)
            .setParameter("snapped_lon", s.snappedLon)
            .setParameter("updated_at", s.updatedAt)
            .executeUpdate()
    }

    private companion object {
        const val UPSERT_SQL = """
            insert into vehicle_state (
                feed_id, vehicle_id, vehicle_label, report_ts, lat, lon, bearing, speed_mps,
                occupancy_status, matched, stale, consecutive_failures, revision_id, trip_row_id,
                block_pk, trip_pattern_id, stop_path_index, distance_along_trip_m,
                schedule_adherence_sec, snapped_lat, snapped_lon, updated_at
            ) values (
                :feed_id, :vehicle_id, :vehicle_label, :report_ts, :lat, :lon, :bearing, :speed_mps,
                :occupancy_status, :matched, :stale, :consecutive_failures, :revision_id, :trip_row_id,
                :block_pk, :trip_pattern_id, :stop_path_index, :distance_along_trip_m,
                :schedule_adherence_sec, :snapped_lat, :snapped_lon, :updated_at
            )
            on conflict (feed_id, vehicle_id) do update set
                vehicle_label = excluded.vehicle_label,
                report_ts = excluded.report_ts,
                lat = excluded.lat, lon = excluded.lon,
                bearing = excluded.bearing, speed_mps = excluded.speed_mps,
                occupancy_status = excluded.occupancy_status,
                matched = excluded.matched, stale = excluded.stale,
                consecutive_failures = excluded.consecutive_failures,
                revision_id = excluded.revision_id, trip_row_id = excluded.trip_row_id,
                block_pk = excluded.block_pk, trip_pattern_id = excluded.trip_pattern_id,
                stop_path_index = excluded.stop_path_index,
                distance_along_trip_m = excluded.distance_along_trip_m,
                schedule_adherence_sec = excluded.schedule_adherence_sec,
                snapped_lat = excluded.snapped_lat, snapped_lon = excluded.snapped_lon,
                updated_at = excluded.updated_at
        """
    }
}
