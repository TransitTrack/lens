package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Typed JPA entities for the core GTFS Schedule files (agency, stops, routes,
 * trips, stop_times, calendar, calendar_dates, feed_info).
 *
 * GTFS enum integers are stored as `SMALLINT`; the corresponding Kotlin `Int?`
 * fields carry `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto:
 * validate` sees matching JDBC type codes.
 */

@Entity
@Table(name = "gtfs_agency")
class GtfsAgency(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var agencyId: String?,
    var agencyName: String?,
    var agencyUrl: String?,
    var agencyTimezone: String?,
    var agencyLang: String?,
    var agencyPhone: String?,
    var agencyFareUrl: String?,
    var agencyEmail: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_stop")
class GtfsStop(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
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
    var stopTimezone: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var wheelchairBoarding: Int?,
    var levelId: String?,
    var platformCode: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_route")
class GtfsRoute(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
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
) : RevisionScoped()

@Entity
@Table(name = "gtfs_trip")
class GtfsTrip(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
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
) : RevisionScoped()

@Entity
@Table(name = "gtfs_stop_time")
class GtfsStopTime(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
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
) : RevisionScoped()

@Entity
@Table(name = "gtfs_calendar")
class GtfsCalendar(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
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
) : RevisionScoped()

@Entity
@Table(name = "gtfs_calendar_date")
class GtfsCalendarDate(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "service_id", nullable = false) var serviceId: String,
    @Column(name = "date", nullable = false) var date: LocalDate,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exceptionType: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_feed_info")
class GtfsFeedInfo(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var feedPublisherName: String?,
    var feedPublisherUrl: String?,
    var feedLang: String?,
    var defaultLang: String?,
    var feedStartDate: LocalDate?,
    var feedEndDate: LocalDate?,
    var feedVersion: String?,
    var feedContactEmail: String?,
    var feedContactUrl: String?,
) : RevisionScoped()
