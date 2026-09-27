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
    fun findByRevisionIdOrderBySeqAsc(revisionId: Long): List<DraftEdit>

    fun findTopByRevisionIdAndUndoneFalseOrderBySeqDesc(revisionId: Long): DraftEdit?

    fun findTopByRevisionIdAndUndoneTrueOrderBySeqAsc(revisionId: Long): DraftEdit?

    fun countByRevisionId(revisionId: Long): Long

    @Modifying @Transactional
    fun deleteByRevisionIdAndUndoneTrue(revisionId: Long): Int

    @Modifying @Transactional
    fun deleteByRevisionId(revisionId: Long): Int
}
