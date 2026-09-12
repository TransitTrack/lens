package eu.transittrack.avl.api

import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

import eu.transittrack.avl.read.dto.AvlReportMatchDto
import eu.transittrack.avl.read.dto.VehicleDto
import eu.transittrack.gtfs.api.dto.StopDto
import eu.transittrack.gtfs.api.dto.TripDto
import eu.transittrack.gtfs.model.StopRepository
import eu.transittrack.gtfs.model.TripRepository
import eu.transittrack.schedule.model.BlockRepository
import eu.transittrack.schedule.model.StopPathRepository
import eu.transittrack.schedule.model.TripPatternRepository
import eu.transittrack.schedule.read.dto.BlockDto
import eu.transittrack.schedule.read.dto.TripPatternDto

/**
 * Field resolvers for [Vehicle]'s references into GTFS / derived-schedule entities. Each is guarded
 * on the needed id being non-null; nested fields of `Trip` / `Block` / `TripPattern` / `Stop` are
 * resolved by the gtfs / schedule resolvers and are not duplicated here.
 */
@Controller
class AvlNestedResolvers(
    private val trips: TripRepository,
    private val stops: StopRepository,
    private val blocks: BlockRepository,
    private val patterns: TripPatternRepository,
    private val stopPaths: StopPathRepository,
) {
    @SchemaMapping(typeName = "Vehicle")
    fun trip(v: VehicleDto): TripDto? {
        val rev = v.revisionId ?: return null
        val id = v.tripRowId ?: return null
        return trips.findById(id).orElse(null)?.let { TripDto.of(it, rev, v.gtfsFeedCode) }
    }

    @SchemaMapping(typeName = "Vehicle")
    fun block(v: VehicleDto): BlockDto? {
        val rev = v.revisionId ?: return null
        val pk = v.blockPk ?: return null
        return blocks.findById(pk).orElse(null)?.let { BlockDto.of(it, rev, v.gtfsFeedCode) }
    }

    @SchemaMapping(typeName = "Vehicle")
    fun pattern(v: VehicleDto): TripPatternDto? {
        val rev = v.revisionId ?: return null
        val pid = v.tripPatternId ?: return null
        return patterns.findById(pid).orElse(null)?.let { TripPatternDto.of(it, rev, v.gtfsFeedCode) }
    }

    @SchemaMapping(typeName = "Vehicle")
    fun currentStop(v: VehicleDto): StopDto? {
        val rev = v.revisionId ?: return null
        val pid = v.tripPatternId ?: return null
        val idx = v.stopPathIndex ?: return null
        val sp = stopPaths.findByTripPatternOrdered(rev, pid).getOrNull(idx) ?: return null
        return stops.findByStopId(rev, sp.stopId)?.let { StopDto.of(it, rev, v.gtfsFeedCode) }
    }

    @SchemaMapping(typeName = "AvlReportMatch")
    fun trip(m: AvlReportMatchDto): TripDto? =
        trips.findById(m.tripRowId).orElse(null)?.let { TripDto.of(it, m.revisionId, m.gtfsFeedCode) }

    @SchemaMapping(typeName = "AvlReportMatch")
    fun block(m: AvlReportMatchDto): BlockDto? {
        val pk = m.blockPk ?: return null
        return blocks.findById(pk).orElse(null)?.let { BlockDto.of(it, m.revisionId, m.gtfsFeedCode) }
    }
}
