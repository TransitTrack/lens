package eu.transittrack.schedule.read

import java.time.LocalDate

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.SchedTripRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.dto.BlockDto
import eu.transittrack.schedule.read.dto.SchedTripDto
import eu.transittrack.schedule.read.dto.StopPathDto
import eu.transittrack.schedule.read.dto.TripPatternDto

@Service
class ScheduleReadService(
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val schedTrips: SchedTripRepository,
    private val blocks: BlockRepository,
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

    fun schedTrip(
        feedCode: String,
        tripId: String,
        revisionId: String?,
    ): SchedTripDto? {
        val rev = resolver.resolve(feedCode, revisionId)
        return schedTrips.findByTripId(rev, tripId)?.let { SchedTripDto.of(it, rev, feedCode) }
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
    ): List<SchedTripDto> {
        val rev = resolver.resolve(feedCode, revisionId)
        val services = serviceDates.activeServiceIds(rev, LocalDate.parse(date))
        if (services.isEmpty()) return emptyList()
        val list =
            if (routeId != null) {
                schedTrips.findByRouteAndServices(rev, routeId, services)
            } else {
                schedTrips.findByServices(rev, services)
            }
        return list.map { SchedTripDto.of(it, rev, feedCode) }
    }

    /** Parses a stored `path_geometry` JSON string into a List for the `JSON` scalar. */
    fun parseGeometry(raw: String?): Any? = raw?.let { json.readValue(it, List::class.java) }

    fun stopPathsOf(
        rev: Long,
        feedCode: String,
        tripPatternId: Long,
    ): List<StopPathDto> =
        stopPaths.findByTripPatternOrdered(rev, tripPatternId).map {
            StopPathDto.of(it, rev, feedCode, parseGeometry(it.pathGeometry))
        }
}
