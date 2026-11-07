package eu.transittrack.schedule.read

import java.time.LocalDate

import org.springframework.stereotype.Service

import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.read.RevisionResolver
import eu.transittrack.schedule.model.Block
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTrip
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.ScheduleTime
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.TripPattern
import eu.transittrack.schedule.model.TripPatternRepository

@Service
class ScheduleReadService(
    private val patterns: TripPatternRepository,
    private val trips: TripRepository,
    private val blocks: BlockRepository,
    private val blockTrips: BlockTripRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val serviceDates: ServiceDateResolver,
    private val resolver: RevisionResolver,
) {
    fun tripPatterns(
        feedCode: String,
        routeId: String?,
        revisionId: String?,
    ): List<TripPattern> {
        val rev = resolver.resolve(feedCode, revisionId)
        val list =
            if (routeId != null) {
                patterns.findByRouteId(rev, routeId)
            } else {
                patterns.findByRevisionId(rev)
            }
        return list
    }

    fun tripPattern(
        feedCode: String,
        patternKey: String,
        revisionId: String?,
    ): TripPattern? {
        val rev = resolver.resolve(feedCode, revisionId)
        return patterns.findByPatternKey(rev, patternKey)
    }

    fun blocks(
        feedCode: String,
        revisionId: String?,
    ): List<Block> {
        val rev = resolver.resolve(feedCode, revisionId)
        return blocks.findByRevisionId(rev)
    }

    fun block(
        feedCode: String,
        blockId: String,
        serviceId: String,
        revisionId: String?,
    ): Block? {
        val rev = resolver.resolve(feedCode, revisionId)
        return blocks.findByBlockAndService(rev, blockId, serviceId)
    }

    fun blocksOnDate(
        feedCode: String,
        date: String,
        revisionId: String?,
    ): List<Block> {
        val rev = resolver.resolve(feedCode, revisionId)
        val services = serviceDates.activeServiceIds(rev, LocalDate.parse(date))
        if (services.isEmpty()) return emptyList()
        return blocks.findByServices(rev, services)
    }

    fun tripsOnDate(
        feedCode: String,
        date: String,
        routeId: String?,
        revisionId: String?,
    ): List<Trip> {
        val rev = resolver.resolve(feedCode, revisionId)
        val services = serviceDates.activeServiceIds(rev, LocalDate.parse(date))
        if (services.isEmpty()) return emptyList()
        val list =
            if (routeId != null) {
                trips.findDerivedByRouteAndServices(rev, routeId, services)
            } else {
                trips.findDerivedByServices(rev, services)
            }
        return list
    }

    /** The trips following [patternKey]; empty when the pattern is unknown. */
    fun tripPatternTrips(
        feedCode: String,
        patternKey: String,
        revisionId: String?,
    ): List<Trip> {
        val rev = resolver.resolve(feedCode, revisionId)
        val pattern = patterns.findByPatternKey(rev, patternKey) ?: return emptyList()
        return trips.findByTripPattern(rev, pattern.id!!)
    }

    /**
     * Derived schedule times, paired with their trip, for one `tripId` and/or the trips of one
     * `patternKey`; empty when neither matches.
     */
    fun scheduleTimes(
        feedCode: String,
        tripId: String?,
        patternKey: String?,
        revisionId: String?,
    ): List<Pair<Trip, ScheduleTime>> {
        val rev = resolver.resolve(feedCode, revisionId)
        val selected =
            when {
                patternKey != null -> {
                    val pattern = patterns.findByPatternKey(rev, patternKey) ?: return emptyList()
                    trips.findByTripPattern(rev, pattern.id!!).filter { tripId == null || it.tripId == tripId }
                }

                tripId != null -> {
                    listOfNotNull(trips.findByTripId(rev, tripId))
                }

                else -> {
                    throw IllegalArgumentException("pass tripId and/or patternKey")
                }
            }
        return selected.flatMap { trip -> scheduleTimes.findByTripOrdered(rev, trip.id!!).map { trip to it } }
    }

    /**
     * Block trips in scheduled order, paired with their block; narrowed to one `blockId` and/or one
     * `serviceId` when given.
     */
    fun blockTrips(
        feedCode: String,
        blockId: String?,
        serviceId: String?,
        revisionId: String?,
    ): List<Pair<Block, BlockTrip>> {
        val rev = resolver.resolve(feedCode, revisionId)
        val matching =
            blocks.findByRevisionId(rev).filter {
                (blockId == null || it.blockId == blockId) &&
                    (serviceId == null || it.serviceId == serviceId)
            }
        if (matching.isEmpty()) return emptyList()
        val byBlock = matching.associateBy { it.id!! }
        return blockTrips.findByBlockIdsOrdered(rev, byBlock.keys.toList()).map { byBlock.getValue(it.blockId) to it }
    }
}
