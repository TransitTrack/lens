package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.SequenceGenerator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean

/**
 * Base class for all revision-scoped GTFS entities.
 *
 * Every subclass is scoped to one `gtfs_revision`, so `revisionId` lives here
 * rather than being re-declared on each entity. Subclasses take it as a
 * pass-through constructor parameter: `class Foo(revisionId: Long, ...) :
 * RevisionScoped(revisionId)`.
 *
 * `id` is assigned from the `gtfs_entity_seq` sequence. `allocationSize` MUST
 * match the sequence's `incrementBy` in `0000-init.yaml` (500).
 */
@MappedSuperclass
abstract class RevisionScoped(
    @Column(name = "revision_id", nullable = false)
    var revisionId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gtfsSeq")
    @SequenceGenerator(name = "gtfsSeq", sequenceName = "gtfs_entity_seq", allocationSize = 500)
    var id: Long? = null
}

@NoRepositoryBean
interface RevisionScopedRepository<E : RevisionScoped, ID: Any> : JpaRepository<E, ID> {
    fun findByRevisionId(revisionId: Long): List<E>
    fun deleteByRevisionId(revisionId: Long): Long
}
