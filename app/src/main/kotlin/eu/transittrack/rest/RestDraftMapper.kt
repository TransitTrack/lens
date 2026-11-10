package eu.transittrack.rest

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.rest.generated.model.Draft
import eu.transittrack.rest.generated.model.DraftKind
import eu.transittrack.rest.generated.model.DraftLock
import eu.transittrack.rest.generated.model.RevisionStatus
import eu.transittrack.toScalarValue

@Component
@ConditionalOnRole(Role.API)
class RestDraftMapper(
    private val drafts: DraftService,
    private val feeds: GtfsFeedService,
    private val json: Json,
) {
    fun map(d: GtfsRevision): Draft =
        Draft(
            id = d.id.toString(), feedCode = feeds.codeOf(d.feedId) ?: "", status = RevisionStatus.valueOf(d.status.name),
            kind = DraftKind.valueOf(d.kind.name), version = d.version, derivationStale = d.derivationStale,
            createdAt = d.createdAt.toString(), label = d.label, baseRevisionId = d.baseRevisionId?.toString(),
            lastValidation = d.lastValidation?.let { json.parseToJsonElement(it).toScalarValue() },
            lock = drafts.currentLock(d)?.let { DraftLock(it.editor, it.expiresAt.toString()) },
            createdBy = d.createdBy, rowCounts = d.rowCounts,
        )
}
