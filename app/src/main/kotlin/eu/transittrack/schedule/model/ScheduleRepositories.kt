package eu.transittrack.schedule.model

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.gtfs.model.RevisionScopedRepository
import eu.transittrack.gtfs.model.TripTable

@Repository
@Transactional
class TripPatternRepository : RevisionScopedRepository<TripPattern>(TripPatternTable) {
    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): List<TripPattern> =
        TripPatternTable
            .selectAll()
            .where { (TripPatternTable.revisionId eq revisionId) and (TripPatternTable.routeId eq routeId) }
            .map(TripPatternTable::read)

    fun findByPatternKey(
        revisionId: Long,
        patternKey: String,
    ): TripPattern? =
        TripPatternTable
            .selectAll()
            .where { (TripPatternTable.revisionId eq revisionId) and (TripPatternTable.patternKey eq patternKey) }
            .singleOrNull()
            ?.let(TripPatternTable::read)
}

@Repository
@Transactional
class StopPathRepository : RevisionScopedRepository<StopPath>(StopPathTable) {
    fun findByTripPatternOrdered(
        revisionId: Long,
        tripPatternId: Long,
    ): List<StopPath> =
        StopPathTable
            .selectAll()
            .where { (StopPathTable.revisionId eq revisionId) and (StopPathTable.tripPatternId eq tripPatternId) }
            .orderBy(StopPathTable.stopPathIndex)
            .map(StopPathTable::read)

    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): List<StopPath> =
        StopPathTable
            .selectAll()
            .where { (StopPathTable.revisionId eq revisionId) and (StopPathTable.stopId eq stopId) }
            .map(StopPathTable::read)

    /** The stop ids along [tripPatternIds], pattern by pattern in the given order then by path index, each id once. */
    fun findStopIdsAlongPatterns(
        revisionId: Long,
        tripPatternIds: List<Long>,
    ): List<String> {
        if (tripPatternIds.isEmpty()) return emptyList()
        val rank = tripPatternIds.withIndex().associate { (i, id) -> id to i }
        return StopPathTable
            .select(StopPathTable.tripPatternId, StopPathTable.stopPathIndex, StopPathTable.stopId)
            .where { (StopPathTable.revisionId eq revisionId) and (StopPathTable.tripPatternId inList tripPatternIds) }
            .map { Triple(rank.getValue(it[StopPathTable.tripPatternId]), it[StopPathTable.stopPathIndex], it[StopPathTable.stopId]) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
            .distinct()
    }
}

@Repository
@Transactional
class ScheduleTimeRepository : RevisionScopedRepository<ScheduleTime>(ScheduleTimeTable) {
    fun findByTripOrdered(
        revisionId: Long,
        tripId: Long,
    ): List<ScheduleTime> =
        ScheduleTimeTable
            .selectAll()
            .where { (ScheduleTimeTable.revisionId eq revisionId) and (ScheduleTimeTable.tripId eq tripId) }
            .orderBy(ScheduleTimeTable.stopPathIndex)
            .map(ScheduleTimeTable::read)

    fun findByTripsAtStopPaths(
        revisionId: Long,
        tripIds: Collection<Long>,
        stopPathIndexes: Collection<Int>,
    ): List<ScheduleTime> =
        ScheduleTimeTable
            .selectAll()
            .where {
                (ScheduleTimeTable.revisionId eq revisionId) and
                    (ScheduleTimeTable.tripId inList tripIds) and
                    (ScheduleTimeTable.stopPathIndex inList stopPathIndexes)
            }.map(ScheduleTimeTable::read)

    /**
     * Every clock-time trip's scheduled time at one stop path of a pattern, with its service.
     * Frequency-based trips are left out: their times are offsets from whenever a run starts.
     */
    fun findTimedAtStopPath(
        revisionId: Long,
        tripPatternId: Long,
        stopPathIndex: Int,
    ): List<ServiceStopTime> =
        ScheduleTimeTable
            .join(TripTable, JoinType.INNER, onColumn = TripTable.id, otherColumn = ScheduleTimeTable.tripId)
            .select(ScheduleTimeTable.arrivalSec, ScheduleTimeTable.departureSec, TripTable.serviceId)
            .where {
                (ScheduleTimeTable.revisionId eq revisionId) and
                    (TripTable.tripPatternId eq tripPatternId) and
                    (ScheduleTimeTable.stopPathIndex eq stopPathIndex) and
                    (TripTable.frequencyBased.isNull() or (TripTable.frequencyBased eq false))
            }.map { ServiceStopTime(it[ScheduleTimeTable.arrivalSec], it[ScheduleTimeTable.departureSec], it[TripTable.serviceId]) }
}

/** A trip's scheduled time at a stop (service-day seconds), with the service it runs on. */
data class ServiceStopTime(
    val arrivalSec: Int?,
    val departureSec: Int?,
    val serviceId: String,
)

@Repository
@Transactional
class BlockRepository : RevisionScopedRepository<Block>(BlockTable) {
    fun findByBlockId(
        revisionId: Long,
        blockId: String,
    ): List<Block> =
        BlockTable
            .selectAll()
            .where { (BlockTable.revisionId eq revisionId) and (BlockTable.blockId eq blockId) }
            .map(BlockTable::read)

    fun findByBlockAndService(
        revisionId: Long,
        blockId: String,
        serviceId: String,
    ): Block? =
        BlockTable
            .selectAll()
            .where { (BlockTable.revisionId eq revisionId) and (BlockTable.blockId eq blockId) and (BlockTable.serviceId eq serviceId) }
            .singleOrNull()
            ?.let(BlockTable::read)

    fun findByServices(
        revisionId: Long,
        serviceIds: Collection<String>,
    ): List<Block> =
        BlockTable
            .selectAll()
            .where { (BlockTable.revisionId eq revisionId) and (BlockTable.serviceId inList serviceIds) }
            .map(BlockTable::read)
}

@Repository
@Transactional
class BlockTripRepository : RevisionScopedRepository<BlockTrip>(BlockTripTable) {
    fun findByBlockIdOrdered(
        revisionId: Long,
        blockId: Long,
    ): List<BlockTrip> =
        BlockTripTable
            .selectAll()
            .where { (BlockTripTable.revisionId eq revisionId) and (BlockTripTable.blockId eq blockId) }
            .orderBy(BlockTripTable.listIndex)
            .map(BlockTripTable::read)

    fun findByBlockIdsOrdered(
        revisionId: Long,
        blockIds: List<Long>,
    ): List<BlockTrip> =
        BlockTripTable
            .selectAll()
            .where { (BlockTripTable.revisionId eq revisionId) and (BlockTripTable.blockId inList blockIds) }
            .orderBy(BlockTripTable.blockId to SortOrder.ASC, BlockTripTable.listIndex to SortOrder.ASC)
            .map(BlockTripTable::read)

    fun findByTripId(
        revisionId: Long,
        tripId: Long,
    ): BlockTrip? =
        BlockTripTable
            .selectAll()
            .where { (BlockTripTable.revisionId eq revisionId) and (BlockTripTable.tripId eq tripId) }
            .singleOrNull()
            ?.let(BlockTripTable::read)
}
