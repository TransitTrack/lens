package eu.transittrack.gtfs.api

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

import eu.transittrack.gtfs.api.dto.DraftDto
import eu.transittrack.gtfs.api.dto.DraftEditDto
import eu.transittrack.gtfs.api.dto.DraftJobDto
import eu.transittrack.gtfs.api.dto.DraftLockDto
import eu.transittrack.gtfs.api.dto.ForkDraftInput
import eu.transittrack.gtfs.api.dto.RevisionDto
import eu.transittrack.gtfs.draft.DraftEditRepository
import eu.transittrack.gtfs.draft.DraftJob
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.revision.GtfsRevision

@Controller
class DraftController(
    private val drafts: DraftService,
    private val edits: DraftEditRepository,
    private val mapper: DraftMapper,
    private val gtfsMapper: GtfsDtoMapper,
    private val feeds: GtfsFeedRepository,
    private val jobs: DraftJobService,
) {
    private fun jobDto(j: DraftJob) = DraftJobDto(j.id, j.state.name, j.phase.name, j.error)

    private fun feedCodeOf(rev: GtfsRevision): String = feeds.findById(rev.feedId).map { it.code }.orElse("")

    private fun dto(rev: GtfsRevision): DraftDto = mapper.toDto(rev, feedCodeOf(rev), drafts.currentLock(rev))

    @QueryMapping
    fun drafts(
        @Argument feedCode: String?,
    ): List<DraftDto> = drafts.listDrafts(feedCode).map(::dto)

    @QueryMapping
    fun draft(
        @Argument id: String,
    ): DraftDto? = runCatching { drafts.get(id.toLong()) }.getOrNull()?.let(::dto)

    @QueryMapping
    fun draftEdits(
        @Argument id: String,
        @Argument limit: Int,
    ): List<DraftEditDto> = edits.findByRevisionIdOrderBySeqAsc(id.toLong()).takeLast(limit).map(mapper::toDto)

    @QueryMapping
    fun draftJob(
        @Argument jobId: String,
    ): DraftJobDto? = jobs.get(jobId)?.let(::jobDto)

    @MutationMapping
    fun rebuildDraft(
        @Argument id: String,
    ): DraftJobDto {
        drafts.get(id.toLong())
        return jobDto(jobs.submitRebuild(id.toLong()))
    }

    @MutationMapping
    fun forkDraft(
        @Argument input: ForkDraftInput,
    ): DraftDto {
        val rev = drafts.fork(input.feedCode, input.baseRevisionId?.toLong(), input.label, input.editor)
        drafts.claimEditor(rev.id!!, input.editor)
        return dto(drafts.get(rev.id!!))
    }

    @MutationMapping
    fun discardDraft(
        @Argument id: String,
    ): Boolean {
        drafts.discard(id.toLong())
        return true
    }

    @MutationMapping
    fun revertDraftToFork(
        @Argument id: String,
    ): DraftDto = dto(drafts.revertToFork(id.toLong()))

    @MutationMapping
    fun activateDraft(
        @Argument id: String,
        @Argument force: Boolean,
    ): RevisionDto {
        val rev = drafts.activate(id.toLong(), force)
        return gtfsMapper.toDto(rev, feedCodeOf(rev))
    }

    @MutationMapping
    fun claimDraftEditor(
        @Argument id: String,
        @Argument editor: String,
        @Argument takeOver: Boolean,
    ): DraftLockDto = drafts.claimEditor(id.toLong(), editor, takeOver).let { DraftLockDto(it.editor, it.expiresAt.toString()) }

    @MutationMapping
    fun renewDraftEditor(
        @Argument id: String,
        @Argument editor: String,
    ): DraftLockDto = drafts.renewEditor(id.toLong(), editor).let { DraftLockDto(it.editor, it.expiresAt.toString()) }

    @MutationMapping
    fun releaseDraftEditor(
        @Argument id: String,
        @Argument editor: String,
    ): Boolean = drafts.releaseEditor(id.toLong(), editor)
}
