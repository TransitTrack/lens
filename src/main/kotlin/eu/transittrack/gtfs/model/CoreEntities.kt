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
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository

import eu.transittrack.Extent
import eu.transittrack.Point
import eu.transittrack.db.bridge.ExposedBridge

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
    @Column(name = "start_time_sec") var startTimeSec: Int? = null,
    @Column(name = "end_time_sec") var endTimeSec: Int? = null,
    @Column(name = "frequency_based") var frequencyBased: Boolean? = null,
    @Column(name = "no_schedule") var noSchedule: Boolean? = null,
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

/** Hibernate's `LocaleJavaType` storage form: written as [Locale.toString], read back by splitting on `_`. */
private fun localeFromDb(s: String): Locale {
    if (s.isEmpty()) return Locale.ROOT
    val p = s.split('_', limit = 3)
    return when (p.size) {
        1 -> Locale.of(p[0])
        2 -> Locale.of(p[0], p[1])
        else -> Locale.of(p[0], p[1], p[2])
    }
}

object AgencyTable : RevisionScopedTable<Agency>("agencies", Agency::class.java, "fk_agency_rev") {
    val agencyId = varchar("agency_id", 255).nullable()
    val agencyName = varchar("agency_name", 255).nullable()
    val agencyUrl = text("agency_url").nullable()
    val agencyTimezone = varchar("agency_timezone", 64).nullable()
    val agencyLang = varchar("agency_lang", 16).nullable()
    val agencyPhone = varchar("agency_phone", 64).nullable()
    val agencyFareUrl = text("agency_fare_url").nullable()
    val agencyEmail = varchar("agency_email", 255).nullable()
    val extent = ExtentColumns(this)

    init {
        uniqueIndex("uq_agency_rev_id", revisionId, agencyId)
    }

    override fun read(row: ResultRow) =
        Agency(
            revisionId = row[revisionId],
            agencyId = row[agencyId],
            agencyName = row[agencyName],
            agencyUrl = row[agencyUrl],
            agencyTimezone = row[agencyTimezone]?.let(ZoneId::of),
            agencyLang = row[agencyLang]?.let(::localeFromDb),
            agencyPhone = row[agencyPhone],
            agencyFareUrl = row[agencyFareUrl],
            agencyEmail = row[agencyEmail],
            extent = extent.read(row),
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Agency,
    ) {
        s[revisionId] = row.revisionId
        s[agencyId] = row.agencyId
        s[agencyName] = row.agencyName
        s[agencyUrl] = row.agencyUrl
        s[agencyTimezone] = row.agencyTimezone?.id
        s[agencyLang] = row.agencyLang?.toString()
        s[agencyPhone] = row.agencyPhone
        s[agencyFareUrl] = row.agencyFareUrl
        s[agencyEmail] = row.agencyEmail
        extent.bind(s, row.extent)
    }
}

object StopTable : RevisionScopedTable<Stop>("stops", Stop::class.java, "fk_stop_rev") {
    val stopId = varchar("stop_id", 255)
    val stopCode = varchar("stop_code", 255).nullable()
    val stopName = varchar("stop_name", 500).nullable()
    val ttsStopName = varchar("tts_stop_name", 500).nullable()
    val stopDesc = text("stop_desc").nullable()
    val stopLat = double("stop_lat").nullable()
    val stopLon = double("stop_lon").nullable()
    val zoneId = varchar("zone_id", 255).nullable()
    val stopUrl = text("stop_url").nullable()
    val locationType = short("location_type").nullable()
    val parentStation = varchar("parent_station", 255).nullable()
    val stopTimezone = varchar("stop_timezone", 64).nullable()
    val wheelchairBoarding = short("wheelchair_boarding").nullable()
    val levelId = varchar("level_id", 255).nullable()
    val platformCode = varchar("platform_code", 255).nullable()
    val timePointStop = bool("time_point_stop").nullable()
    val layoverStop = bool("layover_stop").nullable()
    val waitStop = bool("wait_stop").nullable()
    val hidden = bool("hidden").nullable()

    init {
        uniqueIndex("uq_stop_rev_id", revisionId, stopId)
        index("ix_stop_rev_parent", false, revisionId, parentStation)
    }

    override fun read(row: ResultRow) =
        Stop(
            revisionId = row[revisionId],
            stopId = row[stopId],
            stopCode = row[stopCode],
            stopName = row[stopName],
            ttsStopName = row[ttsStopName],
            stopDesc = row[stopDesc],
            stopLat = row[stopLat],
            stopLon = row[stopLon],
            zoneId = row[zoneId],
            stopUrl = row[stopUrl],
            locationType = row[locationType]?.toInt(),
            parentStation = row[parentStation],
            stopTimezone = row[stopTimezone]?.let(ZoneId::of),
            wheelchairBoarding = row[wheelchairBoarding]?.toInt(),
            levelId = row[levelId],
            platformCode = row[platformCode],
            timePointStop = row[timePointStop],
            layoverStop = row[layoverStop],
            waitStop = row[waitStop],
            hidden = row[hidden],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Stop,
    ) {
        s[revisionId] = row.revisionId
        s[stopId] = row.stopId
        s[stopCode] = row.stopCode
        s[stopName] = row.stopName
        s[ttsStopName] = row.ttsStopName
        s[stopDesc] = row.stopDesc
        s[stopLat] = row.stopLat
        s[stopLon] = row.stopLon
        s[zoneId] = row.zoneId
        s[stopUrl] = row.stopUrl
        s[locationType] = row.locationType?.toShort()
        s[parentStation] = row.parentStation
        s[stopTimezone] = row.stopTimezone?.id
        s[wheelchairBoarding] = row.wheelchairBoarding?.toShort()
        s[levelId] = row.levelId
        s[platformCode] = row.platformCode
        s[timePointStop] = row.timePointStop
        s[layoverStop] = row.layoverStop
        s[waitStop] = row.waitStop
        s[hidden] = row.hidden
    }
}

object RouteTable : RevisionScopedTable<Route>("routes", Route::class.java, "fk_route_rev") {
    val routeId = varchar("route_id", 255)
    val agencyId = varchar("agency_id", 255).nullable()
    val routeShortName = varchar("route_short_name", 255).nullable()
    val routeLongName = varchar("route_long_name", 500).nullable()
    val routeDesc = text("route_desc").nullable()
    val routeType = short("route_type").nullable()
    val routeUrl = text("route_url").nullable()
    val routeColor = varchar("route_color", 7).nullable()
    val routeTextColor = varchar("route_text_color", 7).nullable()
    val routeSortOrder = integer("route_sort_order").nullable()
    val continuousPickup = short("continuous_pickup").nullable()
    val continuousDropOff = short("continuous_drop_off").nullable()
    val networkId = varchar("network_id", 255).nullable()

    // Nullable with a `false` default in the schema; the row class (like the JPA mapping) never holds null.
    val hidden = bool("hidden").default(false).nullable()
    val maxDistance = double("max_distance").nullable()
    val extent = ExtentColumns(this)

    init {
        uniqueIndex("uq_route_rev_id", revisionId, routeId)
        index("ix_route_rev_agency", false, revisionId, agencyId)
    }

    override fun read(row: ResultRow) =
        Route(
            revisionId = row[revisionId],
            routeId = row[routeId],
            agencyId = row[agencyId],
            routeShortName = row[routeShortName],
            routeLongName = row[routeLongName],
            routeDesc = row[routeDesc],
            routeType = row[routeType]?.toInt(),
            routeUrl = row[routeUrl],
            routeColor = row[routeColor],
            routeTextColor = row[routeTextColor],
            routeSortOrder = row[routeSortOrder],
            continuousPickup = row[continuousPickup]?.toInt(),
            continuousDropOff = row[continuousDropOff]?.toInt(),
            networkId = row[networkId],
            maxDistance = row[maxDistance],
            hidden = row[hidden] ?: false,
            extent = extent.read(row),
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Route,
    ) {
        s[revisionId] = row.revisionId
        s[routeId] = row.routeId
        s[agencyId] = row.agencyId
        s[routeShortName] = row.routeShortName
        s[routeLongName] = row.routeLongName
        s[routeDesc] = row.routeDesc
        s[routeType] = row.routeType?.toShort()
        s[routeUrl] = row.routeUrl
        s[routeColor] = row.routeColor
        s[routeTextColor] = row.routeTextColor
        s[routeSortOrder] = row.routeSortOrder
        s[continuousPickup] = row.continuousPickup?.toShort()
        s[continuousDropOff] = row.continuousDropOff?.toShort()
        s[networkId] = row.networkId
        s[hidden] = row.hidden
        s[maxDistance] = row.maxDistance
        extent.bind(s, row.extent)
    }
}

object TripTable : RevisionScopedTable<Trip>("trips", Trip::class.java, "fk_trip_rev") {
    val routeId = varchar("route_id", 255)
    val serviceId = varchar("service_id", 255)
    val tripId = varchar("trip_id", 255)
    val tripHeadsign = varchar("trip_headsign", 500).nullable()
    val tripShortName = varchar("trip_short_name", 255).nullable()
    val directionId = short("direction_id").nullable()
    val blockId = varchar("block_id", 255).nullable()
    val shapeId = varchar("shape_id", 255).nullable()
    val wheelchairAccessible = short("wheelchair_accessible").nullable()
    val bikesAllowed = short("bikes_allowed").nullable()
    val tripPatternId = long("trip_pattern_id").nullable()
    val startTimeSec = integer("start_time_sec").nullable()
    val endTimeSec = integer("end_time_sec").nullable()
    val frequencyBased = bool("frequency_based").nullable()
    val noSchedule = bool("no_schedule").nullable()

    init {
        uniqueIndex("uq_trip_rev_id", revisionId, tripId)
        index("ix_trip_rev_route", false, revisionId, routeId)
        index("ix_trip_rev_service", false, revisionId, serviceId)
        index("ix_trip_rev_shape", false, revisionId, shapeId)
        index("ix_trip_rev_pattern", false, revisionId, tripPatternId)
        index("ix_trip_pattern", false, tripPatternId)
    }

    override fun read(row: ResultRow) =
        Trip(
            revisionId = row[revisionId],
            routeId = row[routeId],
            serviceId = row[serviceId],
            tripId = row[tripId],
            tripHeadsign = row[tripHeadsign],
            tripShortName = row[tripShortName],
            directionId = row[directionId]?.toInt(),
            blockId = row[blockId],
            shapeId = row[shapeId],
            wheelchairAccessible = row[wheelchairAccessible]?.toInt(),
            bikesAllowed = row[bikesAllowed]?.toInt(),
            tripPatternId = row[tripPatternId],
            startTimeSec = row[startTimeSec],
            endTimeSec = row[endTimeSec],
            frequencyBased = row[frequencyBased],
            noSchedule = row[noSchedule],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Trip,
    ) {
        s[revisionId] = row.revisionId
        s[routeId] = row.routeId
        s[serviceId] = row.serviceId
        s[tripId] = row.tripId
        s[tripHeadsign] = row.tripHeadsign
        s[tripShortName] = row.tripShortName
        s[directionId] = row.directionId?.toShort()
        s[blockId] = row.blockId
        s[shapeId] = row.shapeId
        s[wheelchairAccessible] = row.wheelchairAccessible?.toShort()
        s[bikesAllowed] = row.bikesAllowed?.toShort()
        s[tripPatternId] = row.tripPatternId
        s[startTimeSec] = row.startTimeSec
        s[endTimeSec] = row.endTimeSec
        s[frequencyBased] = row.frequencyBased
        s[noSchedule] = row.noSchedule
    }
}

object StopTimeTable : RevisionScopedTable<StopTime>("stop_times", StopTime::class.java, "fk_stop_time_rev") {
    val tripId = varchar("trip_id", 255)
    val arrivalTime = integer("arrival_time").nullable()
    val departureTime = integer("departure_time").nullable()
    val stopId = varchar("stop_id", 255).nullable()
    val locationGroupId = varchar("location_group_id", 255).nullable()
    val locationId = varchar("location_id", 255).nullable()
    val stopSequence = integer("stop_sequence")
    val stopHeadsign = varchar("stop_headsign", 500).nullable()
    val startPickupDropOffWindow = integer("start_pickup_drop_off_window").nullable()
    val endPickupDropOffWindow = integer("end_pickup_drop_off_window").nullable()
    val pickupType = short("pickup_type").nullable()
    val dropOffType = short("drop_off_type").nullable()
    val continuousPickup = short("continuous_pickup").nullable()
    val continuousDropOff = short("continuous_drop_off").nullable()
    val shapeDistTraveled = double("shape_dist_traveled").nullable()
    val timepoint = short("timepoint").nullable()
    val pickupBookingRuleId = varchar("pickup_booking_rule_id", 255).nullable()
    val dropOffBookingRuleId = varchar("drop_off_booking_rule_id", 255).nullable()

    init {
        uniqueIndex("uq_stop_time_rev_trip_seq", revisionId, tripId, stopSequence)
        index("ix_stop_time_rev_stop", false, revisionId, stopId)
    }

    override fun read(row: ResultRow) =
        StopTime(
            revisionId = row[revisionId],
            tripId = row[tripId],
            stopSequence = row[stopSequence],
            stopId = row[stopId],
            arrivalTime = row[arrivalTime],
            departureTime = row[departureTime],
            locationGroupId = row[locationGroupId],
            locationId = row[locationId],
            stopHeadsign = row[stopHeadsign],
            startPickupDropOffWindow = row[startPickupDropOffWindow],
            endPickupDropOffWindow = row[endPickupDropOffWindow],
            pickupType = row[pickupType]?.toInt(),
            dropOffType = row[dropOffType]?.toInt(),
            continuousPickup = row[continuousPickup]?.toInt(),
            continuousDropOff = row[continuousDropOff]?.toInt(),
            shapeDistTraveled = row[shapeDistTraveled],
            timepoint = row[timepoint]?.toInt(),
            pickupBookingRuleId = row[pickupBookingRuleId],
            dropOffBookingRuleId = row[dropOffBookingRuleId],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: StopTime,
    ) {
        s[revisionId] = row.revisionId
        s[tripId] = row.tripId
        s[arrivalTime] = row.arrivalTime
        s[departureTime] = row.departureTime
        s[stopId] = row.stopId
        s[locationGroupId] = row.locationGroupId
        s[locationId] = row.locationId
        s[stopSequence] = row.stopSequence
        s[stopHeadsign] = row.stopHeadsign
        s[startPickupDropOffWindow] = row.startPickupDropOffWindow
        s[endPickupDropOffWindow] = row.endPickupDropOffWindow
        s[pickupType] = row.pickupType?.toShort()
        s[dropOffType] = row.dropOffType?.toShort()
        s[continuousPickup] = row.continuousPickup?.toShort()
        s[continuousDropOff] = row.continuousDropOff?.toShort()
        s[shapeDistTraveled] = row.shapeDistTraveled
        s[timepoint] = row.timepoint?.toShort()
        s[pickupBookingRuleId] = row.pickupBookingRuleId
        s[dropOffBookingRuleId] = row.dropOffBookingRuleId
    }
}

object CalendarTable : RevisionScopedTable<Calendar>("calendars", Calendar::class.java, "fk_calendar_rev") {
    val serviceId = varchar("service_id", 255)
    val monday = bool("monday").nullable()
    val tuesday = bool("tuesday").nullable()
    val wednesday = bool("wednesday").nullable()
    val thursday = bool("thursday").nullable()
    val friday = bool("friday").nullable()
    val saturday = bool("saturday").nullable()
    val sunday = bool("sunday").nullable()
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()

    init {
        uniqueIndex("uq_calendar_rev_service", revisionId, serviceId)
    }

    override fun read(row: ResultRow) =
        Calendar(
            revisionId = row[revisionId],
            serviceId = row[serviceId],
            monday = row[monday],
            tuesday = row[tuesday],
            wednesday = row[wednesday],
            thursday = row[thursday],
            friday = row[friday],
            saturday = row[saturday],
            sunday = row[sunday],
            startDate = row[startDate],
            endDate = row[endDate],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Calendar,
    ) {
        s[revisionId] = row.revisionId
        s[serviceId] = row.serviceId
        s[monday] = row.monday
        s[tuesday] = row.tuesday
        s[wednesday] = row.wednesday
        s[thursday] = row.thursday
        s[friday] = row.friday
        s[saturday] = row.saturday
        s[sunday] = row.sunday
        s[startDate] = row.startDate
        s[endDate] = row.endDate
    }
}

object CalendarDateTable : RevisionScopedTable<CalendarDate>("calendar_dates", CalendarDate::class.java, "fk_calendar_date_rev") {
    val serviceId = varchar("service_id", 255)
    val date = date("date")
    val exceptionType = short("exception_type").nullable()

    init {
        uniqueIndex("uq_calendar_date_rev_service_date", revisionId, serviceId, date)
    }

    override fun read(row: ResultRow) =
        CalendarDate(
            revisionId = row[revisionId],
            serviceId = row[serviceId],
            date = row[date],
            exceptionType = row[exceptionType]?.toInt(),
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: CalendarDate,
    ) {
        s[revisionId] = row.revisionId
        s[serviceId] = row.serviceId
        s[date] = row.date
        s[exceptionType] = row.exceptionType?.toShort()
    }
}

object FeedInfoTable : RevisionScopedTable<FeedInfo>("feed_infos", FeedInfo::class.java, "fk_feed_info_rev") {
    val feedPublisherName = varchar("feed_publisher_name", 255).nullable()
    val feedPublisherUrl = text("feed_publisher_url").nullable()
    val feedLang = varchar("feed_lang", 16).nullable()
    val defaultLang = varchar("default_lang", 16).nullable()
    val feedStartDate = date("feed_start_date").nullable()
    val feedEndDate = date("feed_end_date").nullable()
    val feedVersion = varchar("feed_version", 255).nullable()
    val feedContactEmail = varchar("feed_contact_email", 255).nullable()
    val feedContactUrl = text("feed_contact_url").nullable()

    init {
        uniqueIndex("uq_feed_info_rev", revisionId)
    }

    override fun read(row: ResultRow) =
        FeedInfo(
            revisionId = row[revisionId],
            feedPublisherName = row[feedPublisherName],
            feedPublisherUrl = row[feedPublisherUrl],
            feedLang = row[feedLang],
            defaultLang = row[defaultLang],
            feedStartDate = row[feedStartDate],
            feedEndDate = row[feedEndDate],
            feedVersion = row[feedVersion],
            feedContactEmail = row[feedContactEmail],
            feedContactUrl = row[feedContactUrl],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: FeedInfo,
    ) {
        s[revisionId] = row.revisionId
        s[feedPublisherName] = row.feedPublisherName
        s[feedPublisherUrl] = row.feedPublisherUrl
        s[feedLang] = row.feedLang
        s[defaultLang] = row.defaultLang
        s[feedStartDate] = row.feedStartDate
        s[feedEndDate] = row.feedEndDate
        s[feedVersion] = row.feedVersion
        s[feedContactEmail] = row.feedContactEmail
        s[feedContactUrl] = row.feedContactUrl
    }
}

object FrequencyTable : RevisionScopedTable<Frequency>("frequencies", Frequency::class.java, "fk_frequency_rev") {
    val tripId = varchar("trip_id", 255)
    val startTime = integer("start_time")
    val endTime = integer("end_time").nullable()
    val headwaySecs = integer("headway_secs").nullable()
    val exactTimes = short("exact_times").nullable()

    init {
        uniqueIndex("uq_frequency_rev_trip_start", revisionId, tripId, startTime)
        index("ix_frequency_rev_trip", false, revisionId, tripId)
    }

    override fun read(row: ResultRow) =
        Frequency(
            revisionId = row[revisionId],
            tripId = row[tripId],
            startTime = row[startTime],
            endTime = row[endTime],
            headwaySecs = row[headwaySecs],
            exactTimes = row[exactTimes]?.toInt(),
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Frequency,
    ) {
        s[revisionId] = row.revisionId
        s[tripId] = row.tripId
        s[startTime] = row.startTime
        s[endTime] = row.endTime
        s[headwaySecs] = row.headwaySecs
        s[exactTimes] = row.exactTimes?.toShort()
    }
}

@Repository
@ExposedBridge
class AgencyRepository : RevisionScopedRepository<Agency>(AgencyTable) {
    fun findByAgencyId(
        revisionId: Long,
        agencyId: String,
    ): Agency? =
        AgencyTable
            .selectAll()
            .where { (AgencyTable.revisionId eq revisionId) and (AgencyTable.agencyId eq agencyId) }
            .singleOrNull()
            ?.let(AgencyTable::read)
}

@Repository
@ExposedBridge
class StopRepository : RevisionScopedRepository<Stop>(StopTable) {
    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): Stop? =
        StopTable
            .selectAll()
            .where { (StopTable.revisionId eq revisionId) and (StopTable.stopId eq stopId) }
            .singleOrNull()
            ?.let(StopTable::read)

    fun findByStopIds(
        revisionId: Long,
        stopIds: Collection<String>,
    ): List<Stop> =
        StopTable
            .selectAll()
            .where { (StopTable.revisionId eq revisionId) and (StopTable.stopId inList stopIds) }
            .map(StopTable::read)

    fun findByParentStation(
        revisionId: Long,
        parentStation: String,
    ): List<Stop> =
        StopTable
            .selectAll()
            .where { (StopTable.revisionId eq revisionId) and (StopTable.parentStation eq parentStation) }
            .map(StopTable::read)
}

@Repository
@ExposedBridge
class RouteRepository : RevisionScopedRepository<Route>(RouteTable) {
    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): Route? =
        RouteTable
            .selectAll()
            .where { (RouteTable.revisionId eq revisionId) and (RouteTable.routeId eq routeId) }
            .singleOrNull()
            ?.let(RouteTable::read)
}

@Repository
@ExposedBridge
class TripRepository : RevisionScopedRepository<Trip>(TripTable) {
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): Trip? =
        TripTable
            .selectAll()
            .where { (TripTable.revisionId eq revisionId) and (TripTable.tripId eq tripId) }
            .singleOrNull()
            ?.let(TripTable::read)

    fun findByRouteId(
        revisionId: Long,
        routeId: String,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where { (TripTable.revisionId eq revisionId) and (TripTable.routeId eq routeId) }
            .map(TripTable::read)

    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where { (TripTable.revisionId eq revisionId) and (TripTable.serviceId eq serviceId) }
            .map(TripTable::read)

    fun findByTripPattern(
        revisionId: Long,
        tripPatternId: Long,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where { (TripTable.revisionId eq revisionId) and (TripTable.tripPatternId eq tripPatternId) }
            .map(TripTable::read)

    fun findDerivedByServices(
        revisionId: Long,
        serviceIds: Collection<String>,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where {
                (TripTable.revisionId eq revisionId) and TripTable.tripPatternId.isNotNull() and (TripTable.serviceId inList serviceIds)
            }.map(TripTable::read)

    fun findDerivedByRouteAndServices(
        revisionId: Long,
        routeId: String,
        serviceIds: Collection<String>,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where {
                (TripTable.revisionId eq revisionId) and TripTable.tripPatternId.isNotNull() and
                    (TripTable.routeId eq routeId) and (TripTable.serviceId inList serviceIds)
            }.map(TripTable::read)

    /**
     * Bounded schedule-population projection for the optimization analysis pipeline
     * ([eu.transittrack.schedule.optimize.OptimizationAnalysisPipeline]): only derived trips (a
     * pattern assigned) within the requested optional service/route/direction/scheduled-departure
     * window filters. A null filter is left out of the query (the JPQL's `:x is null or ...`).
     */
    fun findEligible(
        revisionId: Long,
        serviceId: String?,
        routeId: String?,
        directionId: Int?,
        windowFromSec: Int?,
        windowToSec: Int?,
    ): List<Trip> =
        TripTable
            .selectAll()
            .where {
                listOfNotNull<Op<Boolean>>(
                    TripTable.revisionId eq revisionId,
                    TripTable.tripPatternId.isNotNull(),
                    serviceId?.let { TripTable.serviceId eq it },
                    routeId?.let { TripTable.routeId eq it },
                    directionId?.let { TripTable.directionId eq it.toShort() },
                    windowFromSec?.let { TripTable.startTimeSec greaterEq it },
                    windowToSec?.let { TripTable.startTimeSec lessEq it },
                ).reduce { a, b -> a and b }
            }.map(TripTable::read)
}

@Repository
@ExposedBridge
class StopTimeRepository : RevisionScopedRepository<StopTime>(StopTimeTable) {
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): List<StopTime> =
        StopTimeTable
            .selectAll()
            .where { (StopTimeTable.revisionId eq revisionId) and (StopTimeTable.tripId eq tripId) }
            .orderBy(StopTimeTable.stopSequence)
            .map(StopTimeTable::read)

    fun findByStopId(
        revisionId: Long,
        stopId: String,
    ): List<StopTime> =
        StopTimeTable
            .selectAll()
            .where { (StopTimeTable.revisionId eq revisionId) and (StopTimeTable.stopId eq stopId) }
            .map(StopTimeTable::read)

    /**
     * Batch load for schedule derivation: all stop times of a set of trips in one round-trip, ordered
     * so each trip's sublist is already in `stop_sequence` order.
     */
    fun findByTripIds(
        revisionId: Long,
        tripIds: Collection<String>,
    ): List<StopTime> =
        StopTimeTable
            .selectAll()
            .where { (StopTimeTable.revisionId eq revisionId) and (StopTimeTable.tripId inList tripIds) }
            .orderBy(StopTimeTable.tripId to SortOrder.ASC, StopTimeTable.stopSequence to SortOrder.ASC)
            .map(StopTimeTable::read)
}

@Repository
@ExposedBridge
class CalendarRepository : RevisionScopedRepository<Calendar>(CalendarTable) {
    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): Calendar? =
        CalendarTable
            .selectAll()
            .where { (CalendarTable.revisionId eq revisionId) and (CalendarTable.serviceId eq serviceId) }
            .singleOrNull()
            ?.let(CalendarTable::read)
}

@Repository
@ExposedBridge
class CalendarDateRepository : RevisionScopedRepository<CalendarDate>(CalendarDateTable) {
    fun findByServiceId(
        revisionId: Long,
        serviceId: String,
    ): List<CalendarDate> =
        CalendarDateTable
            .selectAll()
            .where { (CalendarDateTable.revisionId eq revisionId) and (CalendarDateTable.serviceId eq serviceId) }
            .map(CalendarDateTable::read)

    fun findDistinctServiceIds(revisionId: Long): List<String> =
        CalendarDateTable
            .select(CalendarDateTable.serviceId)
            .where { CalendarDateTable.revisionId eq revisionId }
            .withDistinct()
            .map { it[CalendarDateTable.serviceId] }
}

@Repository
@ExposedBridge
class FeedInfoRepository : RevisionScopedRepository<FeedInfo>(FeedInfoTable)

@Repository
@ExposedBridge
class FrequencyRepository : RevisionScopedRepository<Frequency>(FrequencyTable) {
    fun findByTripId(
        revisionId: Long,
        tripId: String,
    ): List<Frequency> =
        FrequencyTable
            .selectAll()
            .where { (FrequencyTable.revisionId eq revisionId) and (FrequencyTable.tripId eq tripId) }
            .map(FrequencyTable::read)
}
