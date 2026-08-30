package eu.transittrack.schedule.derive

import eu.transittrack.gtfs.model.RevisionScoped
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.stereotype.Component

data class StopPathAggregateUpdate(
    val stopPathId: Long,
    val typicalTravelTimeSec: Int?,
    val typicalDwellTimeSec: Int?,
    val layoverStop: Boolean,
    val breakTimeSec: Int?,
)

data class SchedTripBlockUpdate(
    val schedTripId: Long,
    val blockSeq: Int,
    val layoverAfterSec: Int?,
    val deadheadAfter: Boolean?,
)

/**
 * Bulk write path for the derived schedule model, backed by a Hibernate
 * [org.hibernate.StatelessSession] (no persistence context; JDBC-batched).
 * Mirrors [eu.transittrack.gtfs.store.StatelessSessionRevisionWriter].
 */
@Component
class ScheduleWriter(emf: EntityManagerFactory) {

    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    private val deleteOrder = listOf("schedule_time", "stop_path", "sched_trip", "block", "trip_pattern")

    fun write(rows: List<RevisionScoped>) {
        if (rows.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (row in rows) session.insert(row)
        }
    }

    fun deleteForRevision(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            for (table in deleteOrder) {
                session.createNativeMutationQuery("delete from $table where revision_id = :r")
                    .setParameter("r", revisionId)
                    .executeUpdate()
            }
        }
    }

    fun applyStopPathAggregates(updates: List<StopPathAggregateUpdate>) {
        if (updates.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (u in updates) {
                session.createNativeMutationQuery(
                    """
                    update stop_path
                       set typical_travel_time_sec = :tt,
                           typical_dwell_time_sec = :td,
                           layover_stop = :lo,
                           break_time_sec = :bt
                     where id = :id
                    """.trimIndent(),
                )
                    .setParameter("tt", u.typicalTravelTimeSec)
                    .setParameter("td", u.typicalDwellTimeSec)
                    .setParameter("lo", u.layoverStop)
                    .setParameter("bt", u.breakTimeSec)
                    .setParameter("id", u.stopPathId)
                    .executeUpdate()
            }
        }
    }

    fun applyTripPatternTripCount(counts: Map<Long, Int>) {
        if (counts.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for ((id, tc) in counts) {
                session.createNativeMutationQuery("update trip_pattern set trip_count = :tc where id = :id")
                    .setParameter("tc", tc)
                    .setParameter("id", id)
                    .executeUpdate()
            }
        }
    }

    fun applySchedTripBlockFields(updates: List<SchedTripBlockUpdate>) {
        if (updates.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (u in updates) {
                session.createNativeMutationQuery(
                    """
                    update sched_trip
                       set block_seq = :bs, layover_after_sec = :la, deadhead_after = :dh
                     where id = :id
                    """.trimIndent(),
                )
                    .setParameter("bs", u.blockSeq)
                    .setParameter("la", u.layoverAfterSec)
                    .setParameter("dh", u.deadheadAfter)
                    .setParameter("id", u.schedTripId)
                    .executeUpdate()
            }
        }
    }
}
