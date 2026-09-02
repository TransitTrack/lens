package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.SequenceGenerator

import com.google.common.reflect.TypeToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.transaction.annotation.Transactional

/**
 * Base class for all revision-scoped GTFS entities.
 *
 * Every subclass is scoped to one `gtfs_revision`, so `revisionId` lives here rather than being
 * re-declared on each entity. Subclasses take it as a pass-through constructor parameter: `class
 * Foo(revisionId: Long, ...) : RevisionScoped(revisionId)`.
 *
 * `id` is assigned from the `gtfs_entity_seq` sequence. `allocationSize` MUST match the sequence's
 * `incrementBy` in `0000-init.yaml` (500).
 */
@MappedSuperclass
abstract class RevisionScoped(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gtfsSeq")
    @SequenceGenerator(name = "gtfsSeq", sequenceName = "gtfs_entity_seq", allocationSize = 500)
    var id:
        Long? = null
}

/**
 * Shared query surface for every [RevisionScoped] entity. Queries use JPQL with the SpEL
 * `#{#entityName}` placeholder so one declaration serves all concrete repositories.
 */
@NoRepositoryBean
interface RevisionScopedRepository<E : RevisionScoped, ID : Any> : JpaRepository<E, ID> {
    @Query("select e from #{#entityName} e where e.revisionId = :revisionId")
    fun findByRevisionId(revisionId: Long): List<E>

    @Modifying @Transactional
    @Query("delete from #{#entityName} e where e.revisionId = :revisionId")
    fun deleteByRevisionId(revisionId: Long): Int

    fun getManagedType(): Class<E> {
        // 1. Capture the runtime class of the concrete subclass (e.g., AgencyRepository)
        val contextToken = TypeToken.of(javaClass)

        // 2. Resolve the concrete type bound to the BaseRepository class declaration
        val superTypeToken = contextToken.getSupertype(RevisionScopedRepository::class.java)

        // 3. Resolve the type parameter E
        val entityType = superTypeToken.resolveType(RevisionScopedRepository::class.java.typeParameters[0])

        // 4. Safely cast and return the raw java class
        @Suppress("UNCHECKED_CAST")
        return entityType.rawType as Class<E>
    }
}
