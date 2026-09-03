package eu.transittrack.schedule.derive

import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import eu.transittrack.gtfs.model.RevisionScoped

data class StopPathLayoverUpdate(
    val stopPathId: Long,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
)

/**
 * Bulk write path for the derived schedule model, backed by a Hibernate
 * [org.hibernate.StatelessSession] (no persistence context; JDBC-batched). Mirrors
 * [eu.transittrack.gtfs.store.StatelessSessionRevisionWriter].
 */
@Component
@ConditionalOnProperty(name = ["transittrack.schedule.enabled"], havingValue = "true")
class ScheduleWriter(
    emf: EntityManagerFactory,
) {
    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    fun write(rows: List<RevisionScoped>) {
        if (rows.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (row in rows) session.insert(row)
        }
    }

    fun deleteForRevision(revisionId: Long) = deleteTables(revisionId, *DELETE_ORDER.toTypedArray())

    fun deleteTables(
        revisionId: Long,
        vararg tables: String,
    ) {
        if (tables.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (table in tables) {
                session
                    .createNativeMutationQuery("delete from $table where revision_id = :r")
                    .setParameter("r", revisionId)
                    .executeUpdate()
            }
        }
    }

    fun applyStopPathLayover(updates: List<StopPathLayoverUpdate>) {
        if (updates.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (u in updates) {
                session
                    .createNativeMutationQuery(
                        "update stop_path set layover_stop = :lo, break_time_sec = :bt where id = :id",
                    ).setParameter("lo", u.layoverStop)
                    .setParameter("bt", u.breakTimeSec)
                    .setParameter("id", u.stopPathId)
                    .executeUpdate()
            }
        }
    }

    fun resetStopPathLayover(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery(
                    "update stop_path set layover_stop = (stop_path_index = 0), break_time_sec = null where revision_id = :r",
                ).setParameter("r", revisionId)
                .executeUpdate()
        }
    }

    fun resetTripPatternTripCount(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            session
                .createNativeMutationQuery("update trip_patterns set trip_count = 0 where revision_id = :r")
                .setParameter("r", revisionId)
                .executeUpdate()
        }
    }

    fun applyTripPatternTripCount(counts: Map<Long, Int>) {
        if (counts.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for ((id, tc) in counts) {
                session
                    .createNativeMutationQuery("update trip_patterns set trip_count = :tc where id = :id")
                    .setParameter("tc", tc)
                    .setParameter("id", id)
                    .executeUpdate()
            }
        }
    }

    companion object {
        val DELETE_ORDER =
            listOf("schedule_time", "block_trip", "travel_times_for_stop_path", "stop_path", "sched_trip", "block", "trip_patterns")
    }
}
