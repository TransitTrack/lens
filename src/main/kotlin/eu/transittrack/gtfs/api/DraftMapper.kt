package eu.transittrack.gtfs.api

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.api.dto.DraftDto
import eu.transittrack.gtfs.api.dto.DraftEditDto
import eu.transittrack.gtfs.api.dto.DraftLockDto
import eu.transittrack.gtfs.draft.DraftEdit
import eu.transittrack.gtfs.draft.DraftLock
import eu.transittrack.gtfs.revision.GtfsRevision

@Component
class DraftMapper(
    private val json: JsonMapper,
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
            status = rev.status.name,
            kind = rev.kind.name,
            version = rev.version,
            derivationStale = rev.derivationStale,
            lastValidation = rev.lastValidation?.let { json.readValue(it, Map::class.java) },
            lock = lock?.let { DraftLockDto(it.editor, it.expiresAt.toString()) },
            createdBy = rev.createdBy,
            createdAt = rev.createdAt.toString(),
            rowCounts = rev.rowCounts,
        )

    fun toDto(edit: DraftEdit) = DraftEditDto(edit.seq, edit.op, edit.summary, edit.appliedAt.toString(), edit.undone)
}
