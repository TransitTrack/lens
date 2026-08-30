package eu.transittrack.schedule.model

import org.springframework.data.jpa.repository.JpaRepository

interface TripPatternRepository : JpaRepository<TripPattern, Long> {
    fun findByRevisionId(revisionId: Long): List<TripPattern>
    fun findByRevisionIdAndRouteId(revisionId: Long, routeId: String): List<TripPattern>
    fun findByRevisionIdAndPatternKey(revisionId: Long, patternKey: String): TripPattern?
}

interface StopPathRepository : JpaRepository<StopPath, Long> {
    fun findByRevisionIdAndTripPatternIdOrderByStopPathIndex(revisionId: Long, tripPatternId: Long): List<StopPath>
    fun findByRevisionIdAndStopId(revisionId: Long, stopId: String): List<StopPath>
}

interface SchedTripRepository : JpaRepository<SchedTrip, Long> {
    fun findByRevisionId(revisionId: Long): List<SchedTrip>
    fun findByRevisionIdAndTripId(revisionId: Long, tripId: String): SchedTrip?
    fun findByRevisionIdAndTripPatternId(revisionId: Long, tripPatternId: Long): List<SchedTrip>
    fun findByRevisionIdAndBlockIdOrderByBlockSeq(revisionId: Long, blockId: String): List<SchedTrip>
    fun findByRevisionIdAndServiceIdIn(revisionId: Long, serviceIds: Collection<String>): List<SchedTrip>
    fun findByRevisionIdAndRouteIdAndServiceIdIn(revisionId: Long, routeId: String, serviceIds: Collection<String>): List<SchedTrip>
}

interface ScheduleTimeRepository : JpaRepository<ScheduleTime, Long> {
    fun findByRevisionIdAndSchedTripIdOrderByStopPathIndex(revisionId: Long, schedTripId: Long): List<ScheduleTime>
}

interface BlockRepository : JpaRepository<Block, Long> {
    fun findByRevisionId(revisionId: Long): List<Block>
    fun findByRevisionIdAndBlockId(revisionId: Long, blockId: String): List<Block>
    fun findByRevisionIdAndBlockIdAndServiceId(revisionId: Long, blockId: String, serviceId: String): Block?
    fun findByRevisionIdAndServiceIdIn(revisionId: Long, serviceIds: Collection<String>): List<Block>
}
