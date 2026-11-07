@file:Suppress("ktlint:standard:max-line-length")

package eu.transittrack.schedule.api

import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.reflect.KClass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.rest.RestEntityMapper
import eu.transittrack.rest.found
import eu.transittrack.rest.generated.api.ScheduleApi
import eu.transittrack.rest.generated.model.Block
import eu.transittrack.rest.generated.model.BlockTrip
import eu.transittrack.rest.generated.model.CalendarChange
import eu.transittrack.rest.generated.model.CalendarChangeKind
import eu.transittrack.rest.generated.model.CalendarExceptionChange
import eu.transittrack.rest.generated.model.ExceptionChangeKind
import eu.transittrack.rest.generated.model.FeedGeometry200Response
import eu.transittrack.rest.generated.model.FeedGeometry200ResponseFeaturesInner
import eu.transittrack.rest.generated.model.FeedGeometry200ResponseFeaturesInnerGeometryOneOf
import eu.transittrack.rest.generated.model.FeedGeometry200ResponseFeaturesInnerGeometryOneOf1
import eu.transittrack.rest.generated.model.HeadwaySummary
import eu.transittrack.rest.generated.model.Problem
import eu.transittrack.rest.generated.model.ScheduleComparison
import eu.transittrack.rest.generated.model.ScheduleTime
import eu.transittrack.rest.generated.model.StopChangeKind
import eu.transittrack.rest.generated.model.StopPath
import eu.transittrack.rest.generated.model.StopTimeChange
import eu.transittrack.rest.generated.model.Trip
import eu.transittrack.rest.generated.model.TripChange
import eu.transittrack.rest.generated.model.TripChangeKind
import eu.transittrack.rest.generated.model.TripPattern
import eu.transittrack.rest.pageOf
import eu.transittrack.schedule.compare.ScheduleComparisonService
import eu.transittrack.schedule.read.FeedGeometryService
import eu.transittrack.schedule.read.ScheduleReadService
import eu.transittrack.toScalarValue

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class ScheduleRestController(
    private val query: ScheduleReadService,
    private val geometry: FeedGeometryService,
    private val comparison: ScheduleComparisonService,
    private val json: Json,
    private val entities: RestEntityMapper,
) : ScheduleApi {
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

    override fun feedGeometry(
        feedCode: String,
        revisionId: String?,
    ): ResponseEntity<FeedGeometry200Response> {
        val compressed = Base64.getDecoder().decode(geometry.feedGeometry(feedCode, revisionId))
        val payload = GZIPInputStream(compressed.inputStream()).bufferedReader().use { it.readText() }
        val features = json.parseToJsonElement(payload).jsonObject.getValue("features").jsonArray.map { item ->
            val feature = item.jsonObject
            val geo = feature.getValue("geometry").jsonObject
            val coordinates = geo.getValue("coordinates").jsonArray
            val shape = when (geo.getValue("type").jsonPrimitive.content) {
                "Point" -> FeedGeometry200ResponseFeaturesInnerGeometryOneOf(
                    "Point",
                    coordinates.map {
                        it.jsonPrimitive.content
                            .toBigDecimal()
                    },
                )

                "LineString" -> FeedGeometry200ResponseFeaturesInnerGeometryOneOf1(
                    "LineString",
                    coordinates.map { pair ->
                        pair.jsonArray.map { it.jsonPrimitive.content.toBigDecimal() }
                    },
                )

                else -> error("Unsupported geometry type")
            }
            FeedGeometry200ResponseFeaturesInner(
                FeedGeometry200ResponseFeaturesInner.Type.Feature, shape,
                feature
                    .getValue("properties")
                    .jsonObject
                    .mapValues { it.value.toScalarValue() },
            )
        }
        return ResponseEntity
            .ok()
            .header("Vary", "Accept-Encoding")
            .contentType(MediaType.parseMediaType("application/geo+json"))
            .body(FeedGeometry200Response(FeedGeometry200Response.Type.FeatureCollection, features))
    }

    override fun block(
        feedCode: String,
        blockId: String,
        serviceId: String,
        revisionId: String?,
    ): ResponseEntity<Block> = found(query.block(feedCode, blockId, serviceId, revisionId), entities::block)

    override fun blocks(
        feedCode: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Block>> = pageOf(offset, limit, { query.blocks(feedCode, revisionId) }) { it.map(entities::block) }

    override fun blocksOnDate(
        feedCode: String,
        date: String,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Block>> = pageOf(offset, limit, { query.blocksOnDate(feedCode, date, revisionId) }) { it.map(entities::block) }

    override fun compareRevisions(
        fromRevisionId: String,
        toRevisionId: String,
        routeId: String?,
        directionId: Int?,
        serviceId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<ScheduleComparison> {
        if (offset < 0 || limit < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
        val result = comparison.compare(fromRevisionId.toLong(), toRevisionId.toLong(), routeId, directionId, serviceId)
        return ResponseEntity.ok(
            ScheduleComparison(
                result.fromRevisionId.toString(), result.toRevisionId.toString(),
                result.fromDerivationStale, result.toDerivationStale, result.tripChanges.size,
                result.tripChanges.drop(offset).take(limit).map { change ->
                    TripChange(
                        change.tripId, TripChangeKind.valueOf(change.kind.name),
                        change.fieldChanges.mapValues { (_, value) -> mapOf("from" to value.first, "to" to value.second) },
                        change.stopChanges.map { stop ->
                            StopTimeChange(
                                stop.stopSequence, StopChangeKind.valueOf(stop.kind.name), stop.stopId,
                                stop.arrivalDeltaSec, stop.departureDeltaSec,
                            )
                        },
                        change.runTimeDeltaSec,
                    )
                },
                result.calendarChanges.map { change ->
                    CalendarChange(
                        change.serviceId, CalendarChangeKind.valueOf(change.kind.name),
                        change.fieldChanges.mapValues { (_, value) -> mapOf("from" to value.first, "to" to value.second) },
                        change.exceptionChanges.map { exception ->
                            CalendarExceptionChange(
                                exception.date.toString(), ExceptionChangeKind.valueOf(exception.kind.name),
                                exception.fromType, exception.toType,
                            )
                        },
                    )
                },
                result.headwaySummaries.map { headway ->
                    HeadwaySummary(
                        routeId = headway.routeId, serviceId = headway.serviceId, directionId = headway.directionId,
                        fromTripCount = headway.fromTripCount, toTripCount = headway.toTripCount,
                        fromMeanGapSec = headway.fromMeanGapSec, toMeanGapSec = headway.toMeanGapSec,
                        fromMaxGapSec = headway.fromMaxGapSec, toMaxGapSec = headway.toMaxGapSec,
                    )
                },
            ),
        )
    }

    override fun tripPattern(
        feedCode: String,
        patternKey: String,
        revisionId: String?,
    ): ResponseEntity<TripPattern> = found(query.tripPattern(feedCode, patternKey, revisionId), entities::tripPattern)

    override fun tripPatterns(
        feedCode: String,
        routeId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<TripPattern>> =
        pageOf(offset, limit, { query.tripPatterns(feedCode, routeId, revisionId) }) { it.map(entities::tripPattern) }

    override fun tripsOnDate(
        feedCode: String,
        date: String,
        routeId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<Trip>> = pageOf(offset, limit, { query.tripsOnDate(feedCode, date, routeId, revisionId) }) { entities.trips(it) }

    override fun blockTrips(
        feedCode: String,
        blockId: String?,
        serviceId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<BlockTrip>> =
        pageOf(offset, limit, { query.blockTrips(feedCode, blockId, serviceId, revisionId) }) { entities.blockTrips(it) }

    override fun scheduleTimes(
        feedCode: String,
        tripId: String?,
        patternKey: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<ScheduleTime>> =
        pageOf(offset, limit, { query.scheduleTimes(feedCode, tripId, patternKey, revisionId) }) { rows ->
            rows.map { (trip, time) -> entities.scheduleTime(trip, time) }
        }

    override fun stopPaths(
        feedCode: String,
        patternKey: String?,
        routeId: String?,
        revisionId: String?,
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<StopPath>> =
        pageOf(offset, limit, {
            val patterns =
                if (patternKey != null) {
                    listOfNotNull(query.tripPattern(feedCode, patternKey, revisionId)).filter { routeId == null || it.routeId == routeId }
                } else {
                    query.tripPatterns(feedCode, routeId, revisionId)
                }
            patterns.flatMap(entities::stopPaths)
        }) { it }
}
