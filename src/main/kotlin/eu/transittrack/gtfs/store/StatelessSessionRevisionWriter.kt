package eu.transittrack.gtfs.store

import jakarta.persistence.EntityManagerFactory

import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedRepository

/**
 * Default [RevisionWriter] backed by a Hibernate [org.hibernate.StatelessSession].
 *
 * A stateless session keeps no persistence context, so a large batch of inserts stays flat in
 * memory; the JDBC driver batches them per `hibernate.jdbc.batch_size` (configured in
 * `application.yaml`).
 */
@Component
class StatelessSessionRevisionWriter(
    emf: EntityManagerFactory,
    repositories: ObjectProvider<RevisionScopedRepository<*, *>>,
) : RevisionWriter {
    companion object {
        val logger = LoggerFactory.getLogger(StatelessSessionRevisionWriter::class.java)
    }

    private val sessionFactory: SessionFactory = emf.unwrap(SessionFactory::class.java)
    private val repositoryMap: Map<Class<*>, RevisionScopedRepository<*, *>> =
        repositories.associateBy {
            it.getManagedType()
        }

    @Transactional
    override fun write(rows: List<RevisionScoped>) {
        if (rows.isEmpty()) return
        val unmapped = ArrayList<RevisionScoped>()
        val groupsOfObjects = rows.groupBy { it.javaClass }

        for ((type, items) in groupsOfObjects) {
            @Suppress("UNCHECKED_CAST")
            val repo = repositoryMap[type] as? RevisionScopedRepository<RevisionScoped, Any>
            if (repo != null) {
                repo.saveAll(items)
            } else {
                logger.error("Unable to save revision scope for type $type")
                unmapped += items
            }
        }

        // Entities whose repository is a plain JpaRepository (e.g. FeedInfo) never land
        // in repositoryMap; insert them directly rather than dropping them silently.
        if (unmapped.isNotEmpty()) {
            sessionFactory.inStatelessTransaction { session ->
                unmapped.forEach { session.insert(it) }
            }
        }
    }

    override fun deleteAllForRevision(revisionId: Long) {
        sessionFactory.inStatelessTransaction { session ->
            for (table in GtfsTables.ALL) {
                session
                    .createNativeMutationQuery(
                        "delete from $table where revision_id = :r",
                    ).setParameter("r", revisionId)
                    .executeUpdate()
            }
        }
    }
}
