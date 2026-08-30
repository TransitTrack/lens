package eu.transittrack.gtfs.model

import org.springframework.data.jpa.repository.JpaRepository

interface GtfsAgencyRepository : JpaRepository<GtfsAgency, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsAgency>
    fun findByRevisionIdAndAgencyId(revisionId: Long, agencyId: String): GtfsAgency?
}

interface GtfsStopRepository : JpaRepository<GtfsStop, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsStop>
    fun findByRevisionIdAndStopId(revisionId: Long, stopId: String): GtfsStop?
    fun findByRevisionIdAndParentStation(revisionId: Long, parentStation: String): List<GtfsStop>
}

interface GtfsRouteRepository : JpaRepository<GtfsRoute, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsRoute>
    fun findByRevisionIdAndRouteId(revisionId: Long, routeId: String): GtfsRoute?
}

interface GtfsTripRepository : JpaRepository<GtfsTrip, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsTrip>
    fun findByRevisionIdAndTripId(revisionId: Long, tripId: String): GtfsTrip?
    fun findByRevisionIdAndRouteId(revisionId: Long, routeId: String): List<GtfsTrip>
    fun findByRevisionIdAndServiceId(revisionId: Long, serviceId: String): List<GtfsTrip>
}

interface GtfsStopTimeRepository : JpaRepository<GtfsStopTime, Long> {
    fun findByRevisionIdAndTripIdOrderByStopSequence(revisionId: Long, tripId: String): List<GtfsStopTime>
    fun findByRevisionIdAndStopId(revisionId: Long, stopId: String): List<GtfsStopTime>
}

interface GtfsCalendarRepository : JpaRepository<GtfsCalendar, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsCalendar>
    fun findByRevisionIdAndServiceId(revisionId: Long, serviceId: String): GtfsCalendar?
}

interface GtfsCalendarDateRepository : JpaRepository<GtfsCalendarDate, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsCalendarDate>
    fun findByRevisionIdAndServiceId(revisionId: Long, serviceId: String): List<GtfsCalendarDate>
}

interface GtfsFeedInfoRepository : JpaRepository<GtfsFeedInfo, Long> {
    fun findByRevisionId(revisionId: Long): GtfsFeedInfo?
}
