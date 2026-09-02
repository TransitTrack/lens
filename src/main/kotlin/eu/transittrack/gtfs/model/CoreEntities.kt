package eu.transittrack.gtfs.model

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

import eu.transittrack.Extent
import eu.transittrack.Point

/**
 * Typed JPA entities for the core GTFS Schedule files (agency, stops, routes, trips, stop_times,
 * calendar, calendar_dates, feed_info, frequencies).
 *
 * GTFS enum integers are stored as `SMALLINT`; the corresponding Kotlin `Int?` fields carry
 * `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate` sees matching JDBC type
 * codes.
 */
@Entity
@Table(name = "agencies")
class Agency(
    revisionId: Long,
    var agencyId: String?,
    var agencyName: String?,
    var agencyUrl: String?,
    var agencyTimezone: ZoneId?,
    var agencyLang: Locale?,
    var agencyPhone: String?,
    var agencyFareUrl: String?,
    var agencyEmail: String?,
    @Embedded var extent: Extent? = null,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "stops")
class Stop(
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
    var timePointStop: Boolean? = null,
    var layoverStop: Boolean? = null,
    var waitStop: Boolean? = null,
    var hidden: Boolean? = null,
) : RevisionScoped(revisionId) {
    val point: Point
        get() = Point(stopLat!!, stopLon!!)
}

@Entity
@Table(name = "routes")
class Route(
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
    var maxDistance: Double? = null,
    @Column(nullable = false) var hidden: Boolean = false,
    @Embedded var extent: Extent? = null,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "trips")
class Trip(
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
    @Column(name = "trip_pattern_id") var tripPatternId: Long? = null,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "stop_times")
class StopTime(
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
@Table(name = "calendars")
class Calendar(
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
@Table(name = "calendar_dates")
class CalendarDate(
    revisionId: Long,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @Column(name = "date", nullable = false) var date: LocalDate,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exceptionType: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "feed_infos")
class FeedInfo(
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
@Table(name = "frequencies")
class Frequency(
    revisionId: Long,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    @Column(name = "start_time", nullable = false) var startTime: Int,
    var endTime: Int?,
    var headwaySecs: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exactTimes: Int?,
) : RevisionScoped(revisionId)

interface AgencyRepository : RevisionScopedRepository<Agency, Long> {
    @Query("select a from Agency a where a.revisionId = :revisionId and a.agencyId = :agencyId")
    fun findByAgencyId(
        revisionId: Long,
        agencyId: String,
    ): Agency?
}

interface StopRepository : RevisionScopedRepository<Stop, Long> {
    @Query("select s from Stop s where s.revisionId = :revisionId and s.stopId = :stopId")
    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): Stop?

    @Query(
        "select s from Stop s where s.revisionId = :revisionId and s.parentStation = :parentStation",
    )
    fun findByParentStation(
        revisionId: Long,
        parentStation: String,
    ): List<Stop>
}

interface RouteRepository : RevisionScopedRepository<Route, Long> {
    @Query("select r from Route r where r.revisionId = :revisionId and r.routeId = :routeId")
    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): Route?
}

interface TripRepository : RevisionScopedRepository<Trip, Long> {
    @Query("select t from Trip t where t.revisionId = :revisionId and t.tripId = :tripId")
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): Trip?

    @Query("select t from Trip t where t.revisionId = :revisionId and t.routeId = :routeId")
    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): List<Trip>

    @Query("select t from Trip t where t.revisionId = :revisionId and t.serviceId = :serviceId")
    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): List<Trip>
}

interface StopTimeRepository : RevisionScopedRepository<StopTime, Long> {
    @Query(
        "select st from StopTime st " +
            "where st.revisionId = :revisionId and st.tripId = :tripId order by st.stopSequence",
    )
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): List<StopTime>

    @Query("select st from StopTime st where st.revisionId = :revisionId and st.stopId = :stopId")
    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): List<StopTime>

    /**
     * Batch load for schedule derivation: all stop times of a set of trips in one round-trip, ordered
     * so each trip's sublist is already in `stop_sequence` order.
     */
    @Query(
        "select st from StopTime st " +
            "where st.revisionId = :revisionId and st.tripId in :tripIds order by st.tripId asc, st.stopSequence asc",
    )
    fun findByTripIds(
        revisionId: Long,
        tripIds: Collection<String>,
    ): List<StopTime>
}

interface CalendarRepository : RevisionScopedRepository<Calendar, Long> {
    @Query("select c from Calendar c where c.revisionId = :revisionId and c.serviceId = :serviceId")
    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): Calendar?
}

interface CalendarDateRepository : RevisionScopedRepository<CalendarDate, Long> {
    @Query(
        "select cd from CalendarDate cd where cd.revisionId = :revisionId and cd.serviceId = :serviceId",
    )
    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): List<CalendarDate>
}

interface FeedInfoRepository : JpaRepository<FeedInfo, Long> {
    @Query("select fi from FeedInfo fi where fi.revisionId = :revisionId")
    fun findByRevisionId(revisionId: Long): FeedInfo?
}

interface FrequencyRepository : RevisionScopedRepository<Frequency, Long> {
    @Query("select f from Frequency f where f.revisionId = :revisionId and f.tripId = :tripId")
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): List<Frequency>
}
