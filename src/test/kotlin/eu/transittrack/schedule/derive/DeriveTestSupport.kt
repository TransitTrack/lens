package eu.transittrack.schedule.derive

import java.time.Instant

import eu.transittrack.gtfs.feed.FeedSource
import eu.transittrack.gtfs.feed.GtfsFeed
import eu.transittrack.gtfs.feed.GtfsFeedRepository
import eu.transittrack.gtfs.model.Agency
import eu.transittrack.gtfs.model.Route
import eu.transittrack.gtfs.model.Stop
import eu.transittrack.gtfs.model.StopTime
import eu.transittrack.gtfs.model.Trip
import eu.transittrack.gtfs.revision.GtfsRevision
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.GtfsRevisionStatus

/** Inserts a feed + a PARSING revision and returns the revision id. */
fun newRevision(
    feeds: GtfsFeedRepository,
    revisions: GtfsRevisionRepository,
): Long {
    val f =
        feeds.save(
            GtfsFeed(
                code = "t-" + System.nanoTime(),
                name = "T",
                description = null,
                url = "http://x/z.zip",
                pollingCron = null,
                enabled = true,
                autoActivate = null,
                source = FeedSource.API,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    return revisions
        .save(
            GtfsRevision(
                feedId = f.id!!,
                status = GtfsRevisionStatus.PARSING,
                sourceUrl = "http://x/z.zip",
                createdAt = Instant.now(),
            ),
        ).id!!
}

fun agency(
    rev: Long,
    id: String,
) = Agency(rev, id, "Agency $id", null, null, null, null, null, null)

fun route(
    rev: Long,
    id: String,
    agencyId: String,
) = Route(
    rev,
    id,
    agencyId,
    id,
    "Route $id",
    null,
    3,
    null,
    null,
    null,
    null,
    null,
    null,
    null,
    maxDistance = null,
    hidden = false,
)

fun trip(
    rev: Long,
    routeId: String,
    tripId: String,
    blockId: String? = null,
) = Trip(rev, routeId, "S", tripId, null, null, null, blockId, null, null, null)

fun stop(
    rev: Long,
    id: String,
    lat: Double,
    lon: Double,
) = Stop(
    rev,
    id,
    null,
    id.uppercase(),
    null,
    null,
    lat,
    lon,
    null,
    null,
    0,
    null,
    null,
    0,
    null,
    null,
    null,
    null,
    null,
    null,
)

fun stopTime(
    rev: Long,
    tripId: String,
    seq: Int,
    stopId: String,
    sec: Int,
) = StopTime(
    rev,
    tripId = tripId,
    stopSequence = seq,
    stopId = stopId,
    arrivalTime = sec,
    departureTime = sec,
    locationGroupId = null,
    locationId = null,
    stopHeadsign = null,
    startPickupDropOffWindow = null,
    endPickupDropOffWindow = null,
    pickupType = null,
    dropOffType = null,
    continuousPickup = null,
    continuousDropOff = null,
    shapeDistTraveled = null,
    timepoint = null,
    pickupBookingRuleId = null,
    dropOffBookingRuleId = null,
)

fun stopTimeNoTimes(
    rev: Long,
    tripId: String,
    seq: Int,
    stopId: String,
) = StopTime(
    rev, tripId = tripId, stopSequence = seq, stopId = stopId, arrivalTime = null, departureTime = null,
    locationGroupId = null, locationId = null, stopHeadsign = null, startPickupDropOffWindow = null,
    endPickupDropOffWindow = null, pickupType = null, dropOffType = null, continuousPickup = null,
    continuousDropOff = null, shapeDistTraveled = null, timepoint = null, pickupBookingRuleId = null,
    dropOffBookingRuleId = null,
)
