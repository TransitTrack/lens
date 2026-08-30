package eu.transittrack.gtfs.store

import eu.transittrack.gtfs.model.RevisionScoped
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.stereotype.Component

/**
 * Default [RevisionWriter] backed by a Hibernate [org.hibernate.StatelessSession].
 *
 * A stateless session keeps no persistence context, so a large batch of inserts
 * stays flat in memory; the JDBC driver batches them per `hibernate.jdbc.batch_size`
 * (configured in `application.yaml`).
 */
@Component
class StatelessSessionRevisionWriter(emf: EntityManagerFactory) : RevisionWriter {

    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)

    override fun write(rows: List<RevisionScoped>) {
        if (rows.isEmpty()) return
        sessionFactory.inStatelessTransaction { session ->
            for (row in rows) session.insert(row)
        }
    }

    override fun deleteAllForRevision(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            for (table in GtfsTables.ALL) {
                session.createNativeMutationQuery("delete from $table where revision_id = :r")
                    .setParameter("r", revisionId)
                    .executeUpdate()
            }
        }
    }
}
