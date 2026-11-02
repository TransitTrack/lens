package eu.transittrack.gtfs.api

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.api.dto.DraftDto
import eu.transittrack.gtfs.api.dto.DraftEditDto
import eu.transittrack.gtfs.api.dto.DraftLockDto
import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.toScalarValue

@Component
@ConditionalOnRole(Role.API)
class DraftMapper(
    private val json: Json,
) {
    fun toDto(
        rev: GtfsRevision,
        feedCode: String,
        lock: DraftLock?,
    ): DraftDto =
        DraftDto(
            id = rev.id.toString(),
            feedCode = feedCode,
            label = rev.label,
            baseRevisionId = rev.baseRevisionId?.toString(),
            status = rev.status,
            kind = rev.kind,
            version = rev.version,
            derivationStale = rev.derivationStale,
            lastValidation = rev.lastValidation?.let { json.parseToJsonElement(it).toScalarValue() },
            lock = lock?.let { DraftLockDto(it.editor, it.expiresAt.toString()) },
            createdBy = rev.createdBy,
            createdAt = rev.createdAt.toString(),
            rowCounts = rev.rowCounts,
        )

    fun toDto(edit: DraftEdit) = DraftEditDto(edit.seq, edit.op, edit.summary, edit.appliedAt.toString(), edit.undone)
}
