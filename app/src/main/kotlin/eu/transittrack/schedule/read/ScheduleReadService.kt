package eu.transittrack.schedule.read

import java.time.LocalDate

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.api.dto.TripDto
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TravelTimesForStopPath
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.dto.BlockDto
import eu.transittrack.schedule.read.dto.StopPathDto
import eu.transittrack.schedule.read.dto.TripPatternDto

@Service
class ScheduleReadService(
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val trips: TripRepository,
    private val blocks: BlockRepository,
    private val travelTimes: TravelTimesForStopPathRepository,
    private val serviceDates: ServiceDateResolver,
    private val resolver: RevisionResolver,
    private val json: JsonMapper,
) {
    fun tripPatterns(
        feedCode: String,
        routeId: String?,
        revisionId: String?,
    ): List<TripPatternDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list =
            if (routeId != null) {
                patterns.findByRouteId(rev, routeId)
            } else {
                patterns.findByRevisionId(rev)
            }
        return list.map { TripPatternDto.of(it, rev, feedCode) }
    }

    fun tripPattern(
        feedCode: String,
        patternKey: String,
        revisionId: String?,
    ): TripPatternDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return patterns.findByPatternKey(rev, patternKey)?.let {
            TripPatternDto.of(it, rev, feedCode)
        }
    }

    fun blocks(
        feedCode: String,
        revisionId: String?,
    ): List<BlockDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        return blocks.findByRevisionId(rev).map { BlockDto.of(it, rev, feedCode) }
    }

    fun block(
        feedCode: String,
        blockId: String,
        serviceId: String,
        revisionId: String?,
    ): BlockDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return blocks.findByBlockAndService(rev, blockId, serviceId)?.let {
            BlockDto.of(it, rev, feedCode)
        }
    }

    fun blocksOnDate(
        feedCode: String,
        date: String,
        revisionId: String?,
    ): List<BlockDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val services = serviceDates.activeServiceIds(rev, LocalDate.parse(date))
        if (services.isEmpty()) return emptyList()
        return blocks.findByServices(rev, services).map { BlockDto.of(it, rev, feedCode) }
    }

    fun tripsOnDate(
        feedCode: String,
        date: String,
        routeId: String?,
        revisionId: String?,
    ): List<TripDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val services = serviceDates.activeServiceIds(rev, LocalDate.parse(date))
        if (services.isEmpty()) return emptyList()
        val list =
            if (routeId != null) {
                trips.findDerivedByRouteAndServices(rev, routeId, services)
            } else {
                trips.findDerivedByServices(rev, services)
            }
        return list.map { TripDto.of(it, rev, feedCode) }
    }

    /** Parses a stored `path_geometry` JSON string into a List for the `JSON` scalar. */
    fun parseGeometry(raw: String?): Any? = raw?.let { json.readValue(it, List::class.java) }

    fun stopPathsOf(
        rev: Long,
        feedCode: String,
        tripPatternId: Long,
    ): List<StopPathDto> =
        stopPaths.findByTripPatternOrdered(rev, tripPatternId).let { paths ->
            val tt = travelTimesOf(rev, tripPatternId)
            paths.map {
                StopPathDto.of(
                    it,
                    rev,
                    feedCode,
                    parseGeometry(it.pathGeometry),
                    tt.getOrNull(it.stopPathIndex)?.travelTimeSec,
                    tt.getOrNull(it.stopPathIndex)?.dwellTimeSec,
                )
            }
        }

    fun travelTimesOf(
        rev: Long,
        tripPatternId: Long,
    ): List<TravelTimesForStopPath> = travelTimes.findByTripPatternOrdered(rev, tripPatternId)
}
