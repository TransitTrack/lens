package eu.transittrack.gtfs.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.SequenceGenerator

/**
 * Base class for all revision-scoped GTFS entities.
 *
 * `id` is assigned from the `gtfs_entity_seq` sequence. `allocationSize` MUST
 * match the sequence's `incrementBy` in `0000-init.yaml` (500).
 */
@MappedSuperclass
abstract class RevisionScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gtfsSeq")
    @SequenceGenerator(name = "gtfsSeq", sequenceName = "gtfs_entity_seq", allocationSize = 500)
    var id: Long? = null
}
