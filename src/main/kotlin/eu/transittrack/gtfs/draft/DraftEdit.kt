package eu.transittrack.gtfs.draft

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(name = "draft_edit")
class DraftEdit(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(nullable = false) var seq: Int,
    @Column(nullable = false) var op: String,
    @Column(nullable = false) var summary: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) var forward: String,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) var inverse: String,
    @Column(name = "applied_at", nullable = false) var appliedAt: Instant,
    @Column(nullable = false) var undone: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "draftEditSeq")
    @SequenceGenerator(name = "draftEditSeq", sequenceName = "draft_edit_seq", allocationSize = 50)
    var id: Long? = null
}

interface DraftEditRepository : JpaRepository<DraftEdit, Long> {
    @Query("select e from DraftEdit e where e.revisionId = :revisionId order by e.seq asc")
    fun findByRevision(
        @Param("revisionId") revisionId: Long,
    ): List<DraftEdit>

    /** The most recently applied (not undone) edit for a revision — the one `undo` would revert. */
    @Query("select e from DraftEdit e where e.revisionId = :revisionId and e.undone = false order by e.seq desc limit 1")
    fun findLastApplied(
        @Param("revisionId") revisionId: Long,
    ): DraftEdit?

    /** The earliest undone edit for a revision — the one `redo` would reapply. */
    @Query("select e from DraftEdit e where e.revisionId = :revisionId and e.undone = true order by e.seq asc limit 1")
    fun findNextRedo(
        @Param("revisionId") revisionId: Long,
    ): DraftEdit?

    @Query("select count(e) from DraftEdit e where e.revisionId = :revisionId")
    fun countByRevision(
        @Param("revisionId") revisionId: Long,
    ): Long

    @Modifying
    @Transactional
    @Query("delete from DraftEdit e where e.revisionId = :revisionId and e.undone = true")
    fun deleteUndone(
        @Param("revisionId") revisionId: Long,
    ): Int

    @Modifying
    @Transactional
    @Query("delete from DraftEdit e where e.revisionId = :revisionId")
    fun deleteForRevision(
        @Param("revisionId") revisionId: Long,
    ): Int
}
