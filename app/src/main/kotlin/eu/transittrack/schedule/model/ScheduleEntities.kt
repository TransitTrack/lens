package eu.transittrack.schedule.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.json.jsonb
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

import eu.transittrack.Extent
import eu.transittrack.gtfs.model.ExtentColumns
import eu.transittrack.gtfs.model.RevisionScoped
import eu.transittrack.gtfs.model.RevisionScopedTable
import eu.transittrack.gtfs.model.TripTable

/**
 * Derived transit model (TransitClock-style) for one GTFS revision. Every row carries the
 * `revision_id` it was derived under; tables cascade-delete with `gtfs_revision`. Clock fields are
 * seconds into the service day.
 */
class TripPattern(
    revisionId: Long,
    var patternKey: String,
    var routeId: String,
    var routeShortName: String?,
    var directionId: Int?,
    var headsign: String?,
    var shapeId: String?,
    var stopCount: Int,
    var lengthM: Double?,
    var extent: Extent = Extent(),
    var tripCount: Int,
) : RevisionScoped(revisionId)

class StopPath(
    revisionId: Long,
    var tripPatternId: Long,
    var stopPathIndex: Int,
    var stopId: String,
    var routeId: String? = null,
    var stopSeq: Int,
    var lengthM: Double,
    var pathGeometry: String?,
    var pickupType: Int?,
    var dropOffType: Int?,
    var waitStop: Boolean,
    var scheduleAdherenceStop: Boolean,
    var layoverStop: Boolean,
    var breakTimeSec: Int?,
) : RevisionScoped(revisionId)

class ScheduleTime(
    revisionId: Long,
    var tripId: Long,
    var stopPathIndex: Int,
    var arrivalSec: Int?,
    var departureSec: Int?,
    var interpolated: Boolean,
    var schedTravelTimeSec: Int?,
    var schedDwellTimeSec: Int?,
) : RevisionScoped(revisionId)

class Block(
    revisionId: Long,
    var blockId: String,
    var serviceId: String,
    var startTimeSec: Int,
    var endTimeSec: Int,
    var tripCount: Int,
    var routeIds: List<String>,
) : RevisionScoped(revisionId)

class BlockTrip(
    revisionId: Long,
    var blockId: Long,
    var tripId: Long,
    var listIndex: Int,
    var layoverAfterSec: Int?,
    var deadheadAfter: Boolean?,
) : RevisionScoped(revisionId)

/** Every derived schedule table, parents before the tables that reference them. */
object ScheduleTables {
    val ALL: List<RevisionScopedTable<*>> =
        listOf(TripPatternTable, StopPathTable, ScheduleTimeTable, BlockTable, BlockTripTable, TravelTimesForStopPathTable)
}

object TripPatternTable : RevisionScopedTable<TripPattern>("trip_patterns", TripPattern::class.java, "fk_trip_pattern_rev") {
    val patternKey = varchar("pattern_key", 255)
    val routeId = varchar("route_id", 255)
    val routeShortName = varchar("route_short_name", 80).nullable()
    val directionId = short("direction_id").nullable()
    val headsign = varchar("headsign", 500).nullable()
    val shapeId = varchar("shape_id", 255).nullable()
    val extent = ExtentColumns(this)
    val stopCount = integer("stop_count")
    val lengthM = double("length_m").nullable()
    val tripCount = integer("trip_count")

    init {
        uniqueIndex("uq_trip_pattern_rev_key", revisionId, patternKey)
        index("ix_trip_pattern_rev_route", false, revisionId, routeId)
    }

    override fun read(row: ResultRow) =
        TripPattern(
            revisionId = row[revisionId],
            patternKey = row[patternKey],
            routeId = row[routeId],
            routeShortName = row[routeShortName],
            directionId = row[directionId]?.toInt(),
            headsign = row[headsign],
            shapeId = row[shapeId],
            stopCount = row[stopCount],
            lengthM = row[lengthM],
            // Derivation always stores an extent (an unset one as four NaNs); the row class never holds null.
            extent = extent.read(row) ?: Extent(),
            tripCount = row[tripCount],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: TripPattern,
    ) {
        s[revisionId] = row.revisionId
        s[patternKey] = row.patternKey
        s[routeId] = row.routeId
        s[routeShortName] = row.routeShortName
        s[directionId] = row.directionId?.toShort()
        s[headsign] = row.headsign
        s[shapeId] = row.shapeId
        extent.bind(s, row.extent)
        s[stopCount] = row.stopCount
        s[lengthM] = row.lengthM
        s[tripCount] = row.tripCount
    }
}

object StopPathTable : RevisionScopedTable<StopPath>("stop_path", StopPath::class.java, "fk_stop_path_rev") {
    val tripPatternId =
        long("trip_pattern_id").references(
            TripPatternTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_stop_path_pattern",
        )
    val stopPathIndex = integer("stop_path_index")
    val stopId = varchar("stop_id", 255)
    val routeId = varchar("route_id", 255).nullable()
    val stopSeq = integer("gtfs_stop_seq")
    val lengthM = double("length_m")
    val pathGeometry = jsonb<String>("path_geometry", { it }, { it }).nullable()
    val pickupType = short("pickup_type").nullable()
    val dropOffType = short("drop_off_type").nullable()
    val waitStop = bool("wait_stop")
    val scheduleAdherenceStop = bool("schedule_adherence_stop")
    val layoverStop = bool("layover_stop")
    val breakTimeSec = integer("break_time_sec").nullable()

    init {
        uniqueIndex("uq_stop_path_rev_pattern_idx", revisionId, tripPatternId, stopPathIndex)
        index("ix_stop_path_rev_stop", false, revisionId, stopId)
        index("ix_stop_path_pattern", false, tripPatternId)
    }

    override fun read(row: ResultRow) =
        StopPath(
            revisionId = row[revisionId],
            tripPatternId = row[tripPatternId],
            stopPathIndex = row[stopPathIndex],
            stopId = row[stopId],
            routeId = row[routeId],
            stopSeq = row[stopSeq],
            lengthM = row[lengthM],
            pathGeometry = row[pathGeometry],
            pickupType = row[pickupType]?.toInt(),
            dropOffType = row[dropOffType]?.toInt(),
            waitStop = row[waitStop],
            scheduleAdherenceStop = row[scheduleAdherenceStop],
            layoverStop = row[layoverStop],
            breakTimeSec = row[breakTimeSec],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: StopPath,
    ) {
        s[revisionId] = row.revisionId
        s[tripPatternId] = row.tripPatternId
        s[stopPathIndex] = row.stopPathIndex
        s[stopId] = row.stopId
        s[routeId] = row.routeId
        s[stopSeq] = row.stopSeq
        s[lengthM] = row.lengthM
        s[pathGeometry] = row.pathGeometry
        s[pickupType] = row.pickupType?.toShort()
        s[dropOffType] = row.dropOffType?.toShort()
        s[waitStop] = row.waitStop
        s[scheduleAdherenceStop] = row.scheduleAdherenceStop
        s[layoverStop] = row.layoverStop
        s[breakTimeSec] = row.breakTimeSec
    }
}

object ScheduleTimeTable : RevisionScopedTable<ScheduleTime>("schedule_time", ScheduleTime::class.java, "fk_schedule_time_rev") {
    val tripId =
        long("trip_id").references(
            TripTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_schedule_time_trip",
        )
    val stopPathIndex = integer("stop_path_index")
    val arrivalSec = integer("arrival_sec").nullable()
    val departureSec = integer("departure_sec").nullable()
    val interpolated = bool("interpolated")
    val schedTravelTimeSec = integer("sched_travel_time_sec").nullable()
    val schedDwellTimeSec = integer("sched_dwell_time_sec").nullable()

    init {
        uniqueIndex("uq_schedule_time_rev_trip_idx", revisionId, tripId, stopPathIndex)
        index("ix_schedule_time_trip", false, tripId)
    }

    override fun read(row: ResultRow) =
        ScheduleTime(
            revisionId = row[revisionId],
            tripId = row[tripId],
            stopPathIndex = row[stopPathIndex],
            arrivalSec = row[arrivalSec],
            departureSec = row[departureSec],
            interpolated = row[interpolated],
            schedTravelTimeSec = row[schedTravelTimeSec],
            schedDwellTimeSec = row[schedDwellTimeSec],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: ScheduleTime,
    ) {
        s[revisionId] = row.revisionId
        s[tripId] = row.tripId
        s[stopPathIndex] = row.stopPathIndex
        s[arrivalSec] = row.arrivalSec
        s[departureSec] = row.departureSec
        s[interpolated] = row.interpolated
        s[schedTravelTimeSec] = row.schedTravelTimeSec
        s[schedDwellTimeSec] = row.schedDwellTimeSec
    }
}

private val jsonMapper: JsonMapper = JsonMapper.builder().build()

object BlockTable : RevisionScopedTable<Block>("block", Block::class.java, "fk_block_rev") {
    val blockId = varchar("block_id", 255)
    val serviceId = varchar("service_id", 255)
    val startTimeSec = integer("start_time_sec")
    val endTimeSec = integer("end_time_sec")
    val tripCount = integer("trip_count")
    val routeIds =
        jsonb<List<String>>(
            "route_ids",
            { jsonMapper.writeValueAsString(it) },
            { jsonMapper.readValue(it, object : TypeReference<List<String>>() {}) },
        )

    init {
        uniqueIndex("uq_block_rev_block_service", revisionId, blockId, serviceId)
        index("ix_block_rev_block", false, revisionId, blockId)
    }

    override fun read(row: ResultRow) =
        Block(
            revisionId = row[revisionId],
            blockId = row[blockId],
            serviceId = row[serviceId],
            startTimeSec = row[startTimeSec],
            endTimeSec = row[endTimeSec],
            tripCount = row[tripCount],
            routeIds = row[routeIds],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Block,
    ) {
        s[revisionId] = row.revisionId
        s[blockId] = row.blockId
        s[serviceId] = row.serviceId
        s[startTimeSec] = row.startTimeSec
        s[endTimeSec] = row.endTimeSec
        s[tripCount] = row.tripCount
        s[routeIds] = row.routeIds
    }
}

object BlockTripTable : RevisionScopedTable<BlockTrip>("block_trip", BlockTrip::class.java, "fk_block_trip_rev") {
    val blockId =
        long("block_id").references(
            BlockTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_block_trip_block",
        )
    val tripId =
        long("trip_id").references(
            TripTable.id,
            onDelete = ReferenceOption.CASCADE,
            onUpdate = ReferenceOption.NO_ACTION,
            fkName = "fk_block_trip_trip",
        )
    val listIndex = integer("list_index")
    val layoverAfterSec = integer("layover_after_sec").nullable()
    val deadheadAfter = bool("deadhead_after").nullable()

    init {
        uniqueIndex("uq_block_trip_rev_block_idx", revisionId, blockId, listIndex)
        uniqueIndex("uq_block_trip_rev_trip", revisionId, tripId)
        index("ix_block_trip_rev_trip", false, revisionId, tripId)
        index("ix_block_trip_block", false, blockId)
        index("ix_block_trip_trip", false, tripId)
    }

    override fun read(row: ResultRow) =
        BlockTrip(
            revisionId = row[revisionId],
            blockId = row[blockId],
            tripId = row[tripId],
            listIndex = row[listIndex],
            layoverAfterSec = row[layoverAfterSec],
            deadheadAfter = row[deadheadAfter],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: BlockTrip,
    ) {
        s[revisionId] = row.revisionId
        s[blockId] = row.blockId
        s[tripId] = row.tripId
        s[listIndex] = row.listIndex
        s[layoverAfterSec] = row.layoverAfterSec
        s[deadheadAfter] = row.deadheadAfter
    }
}
