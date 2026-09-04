package eu.transittrack.schedule.model

import org.springframework.data.jpa.repository.Query

import eu.transittrack.gtfs.model.RevisionScopedRepository

interface TripPatternRepository : RevisionScopedRepository<TripPattern, Long> {
    @Query("select p from TripPattern p where p.revisionId = :revisionId and p.routeId = :routeId")
    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): List<TripPattern>

    @Query("select p from TripPattern p where p.revisionId = :revisionId and p.patternKey = :patternKey")
    fun findByPatternKey(
        revisionId: Long,
        patternKey: String,
    ): TripPattern?
}

interface StopPathRepository : RevisionScopedRepository<StopPath, Long> {
    @Query(
        "select sp from StopPath sp " +
            "where sp.revisionId = :revisionId and sp.tripPatternId = :tripPatternId order by sp.stopPathIndex",
    )
    fun findByTripPatternOrdered(
        revisionId: Long,
        tripPatternId: Long,
    ): List<StopPath>

    @Query("select sp from StopPath sp where sp.revisionId = :revisionId and sp.stopId = :stopId")
    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): List<StopPath>
}

interface ScheduleTimeRepository : RevisionScopedRepository<ScheduleTime, Long> {
    @Query(
        "select st from ScheduleTime st " +
            "where st.revisionId = :revisionId and st.tripId = :tripId order by st.stopPathIndex",
    )
    fun findByTripOrdered(
        revisionId: Long,
        tripId: Long,
    ): List<ScheduleTime>
}

interface BlockRepository : RevisionScopedRepository<Block, Long> {
    @Query("select b from Block b where b.revisionId = :revisionId and b.blockId = :blockId")
    fun findByBlockId(
        revisionId: Long,
        blockId: String,
    ): List<Block>

    @Query(
        "select b from Block b " +
            "where b.revisionId = :revisionId and b.blockId = :blockId and b.serviceId = :serviceId",
    )
    fun findByBlockAndService(
        revisionId: Long,
        blockId: String,
        serviceId: String,
    ): Block?

    @Query("select b from Block b where b.revisionId = :revisionId and b.serviceId in :serviceIds")
    fun findByServices(
        revisionId: Long,
        serviceIds: Collection<String>,
    ): List<Block>
}

interface BlockTripRepository : RevisionScopedRepository<BlockTrip, Long> {
    @Query(
        "select bt from BlockTrip bt " +
            "where bt.revisionId = :revisionId and bt.blockId = :blockId order by bt.listIndex",
    )
    fun findByBlockIdOrdered(
        revisionId: Long,
        blockId: Long,
    ): List<BlockTrip>

    @Query("select bt from BlockTrip bt where bt.revisionId = :revisionId and bt.tripId = :tripId")
    fun findByTripId(
        revisionId: Long,
        tripId: Long,
    ): BlockTrip?
}
