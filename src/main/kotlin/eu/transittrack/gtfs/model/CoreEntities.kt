package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.ZoneId
import java.util.Locale

/**
 * Typed JPA entities for the core GTFS Schedule files (agency, stops, routes,
 * trips, stop_times, calendar, calendar_dates, feed_info, frequencies).
 *
 * GTFS enum integers are stored as `SMALLINT`; the corresponding Kotlin `Int?`
 * fields carry `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto:
 * validate` sees matching JDBC type codes.
 */

@Entity
@Table(name = "gtfs_agency")
class GtfsAgency(
    revisionId: Long,
    var agencyId: String?,
    var agencyName: String?,
    var agencyUrl: String?,
    var agencyTimezone: ZoneId?,
    var agencyLang: Locale?,
    var agencyPhone: String?,
    var agencyFareUrl: String?,
    var agencyEmail: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_stop")
class GtfsStop(
    revisionId: Long,
    @Column(name = "stop_id", nullable = false) var stopId: String,
    var stopCode: String?,
    var stopName: String?,
    var ttsStopName: String?,
    var stopDesc: String?,
    var stopLat: Double?,
    var stopLon: Double?,
    var zoneId: String?,
    var stopUrl: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var locationType: Int?,
    var parentStation: String?,
    var stopTimezone: ZoneId?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var wheelchairBoarding: Int?,
    var levelId: String?,
    var platformCode: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_route")
class GtfsRoute(
    revisionId: Long,
    @Column(name = "route_id", nullable = false) var routeId: String,
    var agencyId: String?,
    var routeShortName: String?,
    var routeLongName: String?,
    var routeDesc: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var routeType: Int?,
    var routeUrl: String?,
    var routeColor: String?,
    var routeTextColor: String?,
    var routeSortOrder: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var continuousPickup: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var continuousDropOff: Int?,
    var networkId: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_trip")
class GtfsTrip(
    revisionId: Long,
    @Column(name = "route_id", nullable = false) var routeId: String,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    var tripHeadsign: String?,
    var tripShortName: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var directionId: Int?,
    var blockId: String?,
    var shapeId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var wheelchairAccessible: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var bikesAllowed: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_stop_time")
class GtfsStopTime(
    revisionId: Long,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    @Column(name = "stop_sequence", nullable = false) var stopSequence: Int,
    var stopId: String?,
    var arrivalTime: Int?,
    var departureTime: Int?,
    var locationGroupId: String?,
    var locationId: String?,
    var stopHeadsign: String?,
    var startPickupDropOffWindow: Int?,
    var endPickupDropOffWindow: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var pickupType: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var dropOffType: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var continuousPickup: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var continuousDropOff: Int?,
    var shapeDistTraveled: Double?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var timepoint: Int?,
    var pickupBookingRuleId: String?,
    var dropOffBookingRuleId: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_calendar")
class GtfsCalendar(
    revisionId: Long,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    var monday: Boolean?,
    var tuesday: Boolean?,
    var wednesday: Boolean?,
    var thursday: Boolean?,
    var friday: Boolean?,
    var saturday: Boolean?,
    var sunday: Boolean?,
    var startDate: LocalDate?,
    var endDate: LocalDate?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_calendar_date")
class GtfsCalendarDate(
    revisionId: Long,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @Column(name = "date", nullable = false) var date: LocalDate,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exceptionType: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_feed_info")
class GtfsFeedInfo(
    revisionId: Long,
    var feedPublisherName: String?,
    var feedPublisherUrl: String?,
    var feedLang: String?,
    var defaultLang: String?,
    var feedStartDate: LocalDate?,
    var feedEndDate: LocalDate?,
    var feedVersion: String?,
    var feedContactEmail: String?,
    var feedContactUrl: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "gtfs_frequency")
class GtfsFrequency(
    revisionId: Long,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    @Column(name = "start_time", nullable = false) var startTime: Int,
    var endTime: Int?,
    var headwaySecs: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exactTimes: Int?,
) : RevisionScoped(revisionId)

interface GtfsAgencyRepository : RevisionScopedRepository<GtfsAgency, Long> {
    @Query("select a from GtfsAgency a where a.revisionId = :revisionId and a.agencyId = :agencyId")
    fun findByAgencyId(revisionId: Long, agencyId: String): GtfsAgency?
}

interface GtfsStopRepository : RevisionScopedRepository<GtfsStop, Long> {
    @Query("select s from GtfsStop s where s.revisionId = :revisionId and s.stopId = :stopId")
    fun findByStopId(revisionId: Long, stopId: String): GtfsStop?

    @Query("select s from GtfsStop s where s.revisionId = :revisionId and s.parentStation = :parentStation")
    fun findByParentStation(revisionId: Long, parentStation: String): List<GtfsStop>
}

interface GtfsRouteRepository : RevisionScopedRepository<GtfsRoute, Long> {
    @Query("select r from GtfsRoute r where r.revisionId = :revisionId and r.routeId = :routeId")
    fun findByRouteId(revisionId: Long, routeId: String): GtfsRoute?
}

interface GtfsTripRepository : RevisionScopedRepository<GtfsTrip, Long> {
    @Query("select t from GtfsTrip t where t.revisionId = :revisionId and t.tripId = :tripId")
    fun findByTripId(revisionId: Long, tripId: String): GtfsTrip?

    @Query("select t from GtfsTrip t where t.revisionId = :revisionId and t.routeId = :routeId")
    fun findByRouteId(revisionId: Long, routeId: String): List<GtfsTrip>

    @Query("select t from GtfsTrip t where t.revisionId = :revisionId and t.serviceId = :serviceId")
    fun findByServiceId(revisionId: Long, serviceId: String): List<GtfsTrip>
}

interface GtfsStopTimeRepository : RevisionScopedRepository<GtfsStopTime, Long> {
    @Query(
        "select st from GtfsStopTime st " +
            "where st.revisionId = :revisionId and st.tripId = :tripId order by st.stopSequence",
    )
    fun findByTripId(revisionId: Long, tripId: String): List<GtfsStopTime>

    @Query("select st from GtfsStopTime st where st.revisionId = :revisionId and st.stopId = :stopId")
    fun findByStopId(revisionId: Long, stopId: String): List<GtfsStopTime>

    /**
     * Batch load for schedule derivation: all stop times of a set of trips in one
     * round-trip, ordered so each trip's sublist is already in `stop_sequence` order.
     */
    @Query(
        "select st from GtfsStopTime st " +
            "where st.revisionId = :revisionId and st.tripId in :tripIds order by st.tripId asc, st.stopSequence asc",
    )
    fun findByTripIds(revisionId: Long, tripIds: Collection<String>): List<GtfsStopTime>
}

interface GtfsCalendarRepository : RevisionScopedRepository<GtfsCalendar, Long> {
    @Query("select c from GtfsCalendar c where c.revisionId = :revisionId and c.serviceId = :serviceId")
    fun findByServiceId(revisionId: Long, serviceId: String): GtfsCalendar?
}

interface GtfsCalendarDateRepository : RevisionScopedRepository<GtfsCalendarDate, Long> {
    @Query("select cd from GtfsCalendarDate cd where cd.revisionId = :revisionId and cd.serviceId = :serviceId")
    fun findByServiceId(revisionId: Long, serviceId: String): List<GtfsCalendarDate>
}

interface GtfsFeedInfoRepository : JpaRepository<GtfsFeedInfo, Long> {
    @Query("select fi from GtfsFeedInfo fi where fi.revisionId = :revisionId")
    fun findByRevisionId(revisionId: Long): GtfsFeedInfo?
}

interface GtfsFrequencyRepository : RevisionScopedRepository<GtfsFrequency, Long> {
    @Query("select f from GtfsFrequency f where f.revisionId = :revisionId and f.tripId = :tripId")
    fun findByTripId(revisionId: Long, tripId: String): List<GtfsFrequency>
}