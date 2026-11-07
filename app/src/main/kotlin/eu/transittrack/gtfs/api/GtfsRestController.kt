@file:Suppress("ktlint:standard:max-line-length")

package eu.transittrack.gtfs.api

import kotlin.reflect.KClass

import io.github.transittrack.gtfsanalyzer.Notice
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.feed.FeedInput
import eu.transittrack.gtfs.feed.GtfsFeedService
import eu.transittrack.gtfs.ingest.IngestionService
import eu.transittrack.gtfs.read.GtfsReadService
import eu.transittrack.gtfs.read.GtfsRecordsService
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.gtfs.revision.GtfsRevisionStatus
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.gtfs.validate.LoadValidationReport
import eu.transittrack.gtfs.validate.countsByEntity
import eu.transittrack.gtfs.validate.toFindings
import eu.transittrack.rest.RestDraftMapper
import eu.transittrack.rest.RestEntityMapper
import eu.transittrack.rest.RestRevisionMapper
import eu.transittrack.rest.found
import eu.transittrack.rest.generated.api.GTFSApi
import eu.transittrack.rest.generated.model.Agency
import eu.transittrack.rest.generated.model.Calendar
import eu.transittrack.rest.generated.model.CalendarDate
import eu.transittrack.rest.generated.model.Draft
import eu.transittrack.rest.generated.model.DraftEdit
import eu.transittrack.rest.generated.model.DraftGrid
import eu.transittrack.rest.generated.model.DraftJob
import eu.transittrack.rest.generated.model.DraftJobPhase
import eu.transittrack.rest.generated.model.DraftJobState
import eu.transittrack.rest.generated.model.EntityFindingCount
import eu.transittrack.rest.generated.model.Feed
import eu.transittrack.rest.generated.model.FeedInfo
import eu.transittrack.rest.generated.model.Frequency
import eu.transittrack.rest.generated.model.Level
import eu.transittrack.rest.generated.model.Pathway
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.RegisterFeedInput
import eu.transittrack.rest.generated.model.Revision
import eu.transittrack.rest.generated.model.RevisionStatus
import eu.transittrack.rest.generated.model.Route
import eu.transittrack.rest.generated.model.Shape
import eu.transittrack.rest.generated.model.Stop
import eu.transittrack.rest.generated.model.StopTime
import eu.transittrack.rest.generated.model.Table
import eu.transittrack.rest.generated.model.Transfer
import eu.transittrack.rest.generated.model.Trip
import eu.transittrack.rest.generated.model.UpdateFeedInput
import eu.transittrack.rest.generated.model.ValidationFinding
import eu.transittrack.rest.pageOf
import eu.transittrack.schedule.read.ScheduleReadService

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class GtfsRestController(
    private val query: GtfsReadService,
    private val schedule: ScheduleReadService,
    private val records: GtfsRecordsService,
    private val feeds: GtfsFeedService,
    private val revisions: RevisionService,
    private val revisionMapper: RestRevisionMapper,
    private val ingestion: IngestionService,
    private val revisionResolver: RevisionResolver,
    private val drafts: DraftService,
    private val edits: DraftEditService,
    private val jobs: DraftJobService,
    private val draftMapper: RestDraftMapper,
    private val grid: RestDraftGrid,
    private val entities: RestEntityMapper,
) : GTFSApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun error(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(
                Problem(
                    title = if (ex.statusCode.value() ==
                        404
                    ) {
                        "Not Found"
                    } else {
                        "Bad Request"
                    },
                    status = ex.statusCode.value(), detail = ex.reason,
                ),
            )

    override fun activateRevision(revisionId: String): ResponseEntity<Revision> {
        val revision = revisions.activate(revisionId.toLong())
        return ResponseEntity.ok(revisionMapper.map(revision, feeds.codeOf(revision.feedId) ?: ""))
    }

    override fun agencies(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Agency>> = pageOf(offset, limit, { query.agencies(feedCode, revisionId) }) { it.map(entities::agency) }

    override fun calendarDates(
        feedCode: String,
        serviceId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<CalendarDate>> =
        pageOf(offset, limit, { query.calendarDates(feedCode, serviceId, revisionId) }) { it.map(entities::calendarDate) }

    override fun calendars(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Calendar>> = pageOf(offset, limit, { query.calendars(feedCode, revisionId) }) { it.map(entities::calendar) }

    override fun deleteFeed(code: String): ResponseEntity<kotlin.Boolean> = ResponseEntity.ok(feeds.delete(code))

    override fun deleteRevision(id: String): ResponseEntity<kotlin.Boolean> {
        val revision = revisions.findOrNull(id.toLong()) ?: return ResponseEntity.ok(false)
        check(revision.status != GtfsRevisionStatus.ACTIVE) { "cannot delete the ACTIVE revision" }
        revisions.deleteWithRows(revision.id!!)
        return ResponseEntity.ok(true)
    }

    override fun draft(id: String): ResponseEntity<Draft> {
        val revision = runCatching { drafts.get(id.toLong()) }.getOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")
        return ResponseEntity.ok(draftMapper.map(revision))
    }

    override fun draftEdits(
        id: String,
        limit: Int,
    ): ResponseEntity<List<DraftEdit>> =
        ResponseEntity.ok(
            edits.editsFor(id.toLong()).takeLast(limit).map {
                DraftEdit(it.seq, it.op, it.summary, it.appliedAt.toString(), it.undone)
            },
        )

    override fun draftGrid(
        draftId: String,
        routeId: String,
        directionId: Int?,
        serviceId: String?,
    ): ResponseEntity<DraftGrid> = ResponseEntity.ok(grid.draftGrid(draftId, routeId, directionId, serviceId))

    override fun draftJob(jobId: String): ResponseEntity<DraftJob> {
        val job = jobs.get(jobId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")
        return ResponseEntity.ok(DraftJob(job.id, DraftJobState.valueOf(job.state.name), DraftJobPhase.valueOf(job.phase.name), job.error))
    }

    override fun drafts(
        feedCode: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Draft>> = pageOf(offset, limit, { drafts.listDrafts(feedCode) }) { it.map(draftMapper::map) }

    override fun feed(code: String): ResponseEntity<Feed> = found(feeds.get(code), entities::feed)

    override fun feedInfo(
        feedCode: String,
        revisionId: String?,
    ): ResponseEntity<FeedInfo> = found(query.feedInfo(feedCode, revisionId), entities::feedInfo)

    override fun feeds(
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Feed>> = pageOf(offset, limit, { feeds.list() }) { it.map(entities::feed) }

    override fun frequencies(
        feedCode: String,
        tripId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Frequency>> =
        pageOf(offset, limit, { query.frequencies(feedCode, tripId, revisionId) }) { it.map(entities::frequency) }

    override fun ingestFeed(feedCode: String): ResponseEntity<Revision> =
        ResponseEntity.ok(revisionMapper.map(ingestion.ingest(feedCode), feedCode))

    override fun levels(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Level>> = pageOf(offset, limit, { query.levels(feedCode, revisionId) }) { it.map(entities::level) }

    override fun pathways(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Pathway>> = pageOf(offset, limit, { query.pathways(feedCode, revisionId) }) { it.map(entities::pathway) }

    override fun records(
        feedCode: String,
        table: Table,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<kotlin.Any>> = pageOf(offset, limit, { records.records(feedCode, table.value, revisionId) }) { it }

    override fun registerFeed(registerFeedInput: RegisterFeedInput): ResponseEntity<Feed> {
        val input = registerFeedInput
        return ResponseEntity.ok(
            entities.feed(
                feeds.register(
                    FeedInput(input.code, input.name, input.description, input.url, input.pollingCron, input.enabled ?: true, input.autoActivate),
                ),
            ),
        )
    }

    override fun revision(id: String): ResponseEntity<Revision> {
        val revision = revisions.findOrNull(id.toLong())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")
        return ResponseEntity.ok(revisionMapper.map(revision, feeds.codeOf(revision.feedId) ?: ""))
    }

    override fun revisions(
        feedCode: String,
        status: RevisionStatus?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Revision>> =
        pageOf(offset, limit, {
            val feed = feeds.get(feedCode)
            if (feed?.id == null) {
                emptyList()
            } else {
                revisions.revisionsForFeed(feed.id!!).filter { status == null || it.status.name == status.name }
            }
        }) { page -> page.map { revisionMapper.map(it, feedCode) } }

    override fun route(
        feedCode: String,
        routeId: String,
        revisionId: String?,
    ): ResponseEntity<Route> = found(query.route(feedCode, routeId, revisionId), entities::route)

    override fun routes(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Route>> = pageOf(offset, limit, { query.routes(feedCode, revisionId) }) { it.map(entities::route) }

    override fun shape(
        feedCode: String,
        shapeId: String,
        revisionId: String?,
    ): ResponseEntity<Shape> = found(query.shape(feedCode, shapeId, revisionId)) { entities.shape(it.shape, it.points) }

    override fun stop(
        feedCode: String,
        stopId: String,
        revisionId: String?,
    ): ResponseEntity<Stop> = found(query.stop(feedCode, stopId, revisionId), entities::stop)

    override fun stopTimes(
        feedCode: String,
        tripId: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<StopTime>> =
        pageOf(offset, limit, { query.stopTimes(feedCode, tripId, revisionId) }) { it.map(entities::stopTime) }

    override fun stops(
        feedCode: String,
        revisionId: String?,
        parentStation: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Stop>> = pageOf(offset, limit, { query.stops(feedCode, revisionId, parentStation) }) { it.map(entities::stop) }

    override fun transfers(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Transfer>> = pageOf(offset, limit, { query.transfers(feedCode, revisionId) }) { it.map(entities::transfer) }

    override fun trip(
        feedCode: String,
        tripId: String,
        revisionId: String?,
    ): ResponseEntity<Trip> = found(query.trip(feedCode, tripId, revisionId)) { entities.trips(listOf(it)).single() }

    override fun trips(
        feedCode: String,
        routeId: String?,
        serviceId: String?,
        blockId: String?,
        patternKey: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Trip>> =
        pageOf(offset, limit, {
            val rows =
                if (patternKey != null) {
                    schedule
                        .tripPatternTrips(feedCode, patternKey, revisionId)
                        .filter { (routeId == null || it.routeId == routeId) && (serviceId == null || it.serviceId == serviceId) }
                } else {
                    query.trips(feedCode, routeId, serviceId, revisionId)
                }
            rows.filter { blockId == null || it.blockId == blockId }
        }) { entities.trips(it) }

    override fun updateFeed(
        code: String,
        updateFeedInput: UpdateFeedInput,
    ): ResponseEntity<Feed> {
        val input = updateFeedInput
        return ResponseEntity.ok(
            entities.feed(
                feeds.update(
                    code,
                    FeedInput(code, input.name, input.description, input.url, input.pollingCron, input.enabled ?: true, input.autoActivate),
                ),
            ),
        )
    }

    override fun validationFindingCounts(
        feedCode: String,
        entityType: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<EntityFindingCount>> =
        pageOf(offset, limit, { noticesOf(feedCode, revisionId).countsByEntity(entityType) }) { it }

    override fun validationFindings(
        feedCode: String,
        entityType: String?,
        entityId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<ValidationFinding>> =
        pageOf(offset, limit, { noticesOf(feedCode, revisionId).toFindings(entityType, entityId) }) { it }

    private fun noticesOf(
        feedCode: String,
        revisionId: String?,
    ): List<Notice> {
        val report = revisions.revision(revisionResolver.resolve(feedCode, revisionId)).validationReport ?: return emptyList()
        return LoadValidationReport
            .fromJson(report)
            ?.result
            ?.notices
            .orEmpty()
    }
}
