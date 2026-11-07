package eu.transittrack.rest

import org.springframework.stereotype.Component

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.geo.BBox
import eu.transittrack.geo.json.toGeoJsonBase64
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.model.AgencyRepository
import eu.transittrack.gtfs.model.LevelRepository
import eu.transittrack.gtfs.model.RouteRepository
import eu.transittrack.gtfs.model.ShapePointRepository
import eu.transittrack.gtfs.model.ShapeRepository
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.StopTimeRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.gtfs.model.compactLineGeometry
import eu.transittrack.rest.generated.model.Agency
import eu.transittrack.rest.generated.model.Block
import eu.transittrack.rest.generated.model.BlockTrip
import eu.transittrack.rest.generated.model.Calendar
import eu.transittrack.rest.generated.model.CalendarDate
import eu.transittrack.rest.generated.model.Extent
import eu.transittrack.rest.generated.model.Feed
import eu.transittrack.rest.generated.model.FeedInfo
import eu.transittrack.rest.generated.model.FeedSource
import eu.transittrack.rest.generated.model.Frequency
import eu.transittrack.rest.generated.model.Level
import eu.transittrack.rest.generated.model.Pathway
import eu.transittrack.rest.generated.model.Route
import eu.transittrack.rest.generated.model.ScheduleTime
import eu.transittrack.rest.generated.model.Shape
import eu.transittrack.rest.generated.model.ShapePoint
import eu.transittrack.rest.generated.model.Stop
import eu.transittrack.rest.generated.model.StopPath
import eu.transittrack.rest.generated.model.StopTime
import eu.transittrack.rest.generated.model.Transfer
import eu.transittrack.rest.generated.model.Trip
import eu.transittrack.rest.generated.model.TripPattern
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.BlockTripRepository
import eu.transittrack.schedule.model.ScheduleTimeRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TravelTimesForStopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.gtfs.model.Agency as AgencyRow
import eu.transittrack.gtfs.model.Calendar as CalendarRow
import eu.transittrack.gtfs.model.CalendarDate as CalendarDateRow
import eu.transittrack.gtfs.model.FeedInfo as FeedInfoRow
import eu.transittrack.gtfs.model.Frequency as FrequencyRow
import eu.transittrack.gtfs.model.Level as LevelRow
import eu.transittrack.gtfs.model.Pathway as PathwayRow
import eu.transittrack.gtfs.model.Route as RouteRow
import eu.transittrack.gtfs.model.Shape as ShapeRow
import eu.transittrack.gtfs.model.ShapePoint as ShapePointRow
import eu.transittrack.gtfs.model.Stop as StopRow
import eu.transittrack.gtfs.model.StopTime as StopTimeRow
import eu.transittrack.gtfs.model.Transfer as TransferRow
import eu.transittrack.gtfs.model.Trip as TripRow
import eu.transittrack.schedule.model.Block as BlockRow
import eu.transittrack.schedule.model.BlockTrip as BlockTripRow
import eu.transittrack.schedule.model.ScheduleTime as ScheduleTimeRow
import eu.transittrack.schedule.model.StopPath as StopPathRow
import eu.transittrack.schedule.model.TripPattern as TripPatternRow

/**
 * Maps GTFS and derived-schedule rows straight to the generated REST models. Models are flat:
 * relationships are exposed as ids and read through their own endpoints. The few ids that are
 * internal row keys on the entity (a trip's pattern, a block trip's trip) are resolved here, in
 * one batched lookup per call.
 */
@Component
@ConditionalOnRole(Role.API)
class RestEntityMapper(
    private val agencies: AgencyRepository,
    private val routes: RouteRepository,
    private val stops: StopRepository,
    private val trips: TripRepository,
    private val stopTimes: StopTimeRepository,
    private val shapes: ShapeRepository,
    private val shapePoints: ShapePointRepository,
    private val levels: LevelRepository,
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
    private val travelTimes: TravelTimesForStopPathRepository,
    private val scheduleTimes: ScheduleTimeRepository,
    private val blocks: BlockRepository,
    private val blockTrips: BlockTripRepository,
) {
    fun agency(e: AgencyRow) =
        Agency(
            agencyId = e.agencyId,
            agencyName = e.agencyName,
            agencyUrl = e.agencyUrl,
            agencyTimezone = e.agencyTimezone?.id,
            agencyLang = e.agencyLang?.toLanguageTag(),
            agencyPhone = e.agencyPhone,
            agencyFareUrl = e.agencyFareUrl,
            agencyEmail = e.agencyEmail,
            extent = extent(e.extent),
        )

    fun route(e: RouteRow) =
        Route(
            routeId = e.routeId,
            agencyId = e.agencyId,
            routeShortName = e.routeShortName,
            routeLongName = e.routeLongName,
            routeDesc = e.routeDesc,
            routeType = e.routeType,
            routeUrl = e.routeUrl,
            routeColor = e.routeColor,
            routeTextColor = e.routeTextColor,
            routeSortOrder = e.routeSortOrder,
            networkId = e.networkId,
            extent = extent(e.extent),
        )

    fun stop(e: StopRow) =
        Stop(
            stopId = e.stopId,
            stopCode = e.stopCode,
            stopName = e.stopName,
            stopDesc = e.stopDesc,
            stopLat = e.stopLat,
            stopLon = e.stopLon,
            zoneId = e.zoneId,
            locationType = e.locationType,
            parentStation = e.parentStation,
            wheelchairBoarding = e.wheelchairBoarding,
            levelId = e.levelId,
            platformCode = e.platformCode,
        )

    /** Maps [rows], resolving each trip's pattern key in a single query. */
    fun trips(rows: List<TripRow>): List<Trip> {
        val patternIds = rows.mapNotNull { it.tripPatternId }.distinct()
        val keys = if (patternIds.isEmpty()) emptyMap() else patterns.findAllById(patternIds).associate { it.id to it.patternKey }
        return rows.map { e ->
            Trip(
                tripId = e.tripId,
                routeId = e.routeId,
                serviceId = e.serviceId,
                tripHeadsign = e.tripHeadsign,
                tripShortName = e.tripShortName,
                directionId = e.directionId,
                blockId = e.blockId,
                shapeId = e.shapeId,
                wheelchairAccessible = e.wheelchairAccessible,
                bikesAllowed = e.bikesAllowed,
                patternKey = e.tripPatternId?.let(keys::get),
                startTimeSec = e.startTimeSec,
                endTimeSec = e.endTimeSec,
                frequencyBased = e.frequencyBased,
                noSchedule = e.noSchedule,
            )
        }
    }

    fun stopTime(e: StopTimeRow) =
        StopTime(
            tripId = e.tripId,
            stopSequence = e.stopSequence,
            stopId = e.stopId,
            arrivalTime = secondsToHms(e.arrivalTime),
            arrivalTimeSeconds = e.arrivalTime,
            departureTime = secondsToHms(e.departureTime),
            departureTimeSeconds = e.departureTime,
            stopHeadsign = e.stopHeadsign,
            pickupType = e.pickupType,
            dropOffType = e.dropOffType,
            shapeDistTraveled = e.shapeDistTraveled,
            timepoint = e.timepoint,
        )

    fun calendar(e: CalendarRow) =
        Calendar(
            e.serviceId, e.monday, e.tuesday, e.wednesday, e.thursday, e.friday, e.saturday, e.sunday,
            e.startDate?.toString(), e.endDate?.toString(),
        )

    fun calendarDate(e: CalendarDateRow) = CalendarDate(e.serviceId, e.date.toString(), e.exceptionType)

    fun frequency(e: FrequencyRow) = Frequency(e.tripId, secondsToHms(e.startTime)!!, secondsToHms(e.endTime), e.headwaySecs, e.exactTimes)

    fun transfer(e: TransferRow) =
        Transfer(e.fromStopId, e.toStopId, e.fromRouteId, e.toRouteId, e.fromTripId, e.toTripId, e.transferType, e.minTransferTime)

    fun feedInfo(e: FeedInfoRow) =
        FeedInfo(
            e.feedPublisherName, e.feedPublisherUrl, e.feedLang, e.defaultLang, e.feedStartDate?.toString(),
            e.feedEndDate?.toString(), e.feedVersion, e.feedContactEmail, e.feedContactUrl,
        )

    fun pathway(e: PathwayRow) =
        Pathway(
            e.pathwayId, e.fromStopId, e.toStopId, e.pathwayMode, e.isBidirectional, e.length, e.traversalTime, e.stairCount,
            e.maxSlope, e.minWidth, e.signpostedAs, e.reversedSignpostedAs,
        )

    fun level(e: LevelRow) = Level(e.levelId, e.levelIndex, e.levelName)

    fun feed(e: GtfsFeed) =
        Feed(
            code = e.code,
            name = e.name,
            description = e.description,
            url = e.url,
            pollingCron = e.pollingCron,
            enabled = e.enabled,
            source = FeedSource.valueOf(e.source.name),
        )

    /** [points] are the shape's rows; the stored geometry wins, else it is computed from them. */
    fun shape(
        shape: ShapeRow,
        points: List<ShapePointRow>,
    ) = Shape(
        shapeId = shape.shapeId,
        pointCount = shape.pointCount,
        points = points.map { ShapePoint(it.shapePtSequence, it.shapePtLat, it.shapePtLon, it.shapeDistTraveled) },
        lengthM = shape.lengthM,
        geometry = shape.geometry
            ?: compactLineGeometry(points.sortedBy { it.shapePtSequence }.map { it.shapePtLat to it.shapePtLon }),
    )

    fun tripPattern(e: TripPatternRow) =
        TripPattern(
            patternKey = e.patternKey,
            routeId = e.routeId,
            stopCount = e.stopCount,
            tripCount = e.tripCount,
            directionId = e.directionId,
            headsign = e.headsign,
            shapeId = e.shapeId,
            lengthM = e.lengthM,
            extent = extent(e.extent),
            geometry = e.geometry?.toGeoJsonBase64(),
        )

    /** A pattern's stop paths in order, each with the typical travel / dwell time at its index. */
    fun stopPaths(pattern: TripPatternRow): List<StopPath> {
        val times = travelTimes.findByTripPatternOrdered(pattern.revisionId, pattern.id!!)
        return stopPaths.findByTripPatternOrdered(pattern.revisionId, pattern.id!!).map { e ->
            val typical = times.getOrNull(e.stopPathIndex)
            StopPath(
                patternKey = pattern.patternKey,
                stopPathIndex = e.stopPathIndex,
                stopId = e.stopId,
                stopSeq = e.stopSeq,
                lengthM = e.lengthM,
                waitStop = e.waitStop,
                scheduleAdherenceStop = e.scheduleAdherenceStop,
                layoverStop = e.layoverStop,
                pickupType = e.pickupType,
                dropOffType = e.dropOffType,
                breakTimeSec = e.breakTimeSec,
                typicalTravelTimeSec = typical?.travelTimeSec,
                typicalDwellTimeSec = typical?.dwellTimeSec,
            )
        }
    }

    fun scheduleTime(
        trip: TripRow,
        e: ScheduleTimeRow,
    ) = ScheduleTime(trip.tripId, e.stopPathIndex, e.interpolated, e.arrivalSec, e.departureSec, e.schedTravelTimeSec, e.schedDwellTimeSec)

    fun block(e: BlockRow) =
        Block(
            id = e.id!!.toString(),
            blockId = e.blockId,
            serviceId = e.serviceId,
            startTimeSec = e.startTimeSec,
            endTimeSec = e.endTimeSec,
            tripCount = e.tripCount,
            routeIds = e.routeIds,
        )

    /** Maps block trips paired with their block, resolving each one's GTFS `trip_id` in a single query. */
    fun blockTrips(rows: List<Pair<BlockRow, BlockTripRow>>): List<BlockTrip> {
        val tripIds = trips.findAllById(rows.map { it.second.tripId }).associate { it.id to it.tripId }
        return rows.map { (block, e) ->
            BlockTrip(e.listIndex, tripIds.getValue(e.tripId), block.blockId, block.serviceId, e.layoverAfterSec, e.deadheadAfter)
        }
    }

    private fun extent(e: BBox?): Extent? = e?.let { Extent(it.minLat, it.minLng, it.maxLat, it.maxLng) }
}

/** GTFS clock times are stored as seconds-of-day (which may exceed 24h); rendered as `HH:MM:SS`. */
internal fun secondsToHms(s: Int?): String? = s?.let { "%02d:%02d:%02d".format(it / 3600, (it % 3600) / 60, it % 60) }
