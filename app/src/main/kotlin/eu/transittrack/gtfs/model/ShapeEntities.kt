package eu.transittrack.gtfs.model

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import eu.transittrack.geo.Line
import eu.transittrack.geo.Position
import eu.transittrack.geo.json.toGeoJsonBase64

/**
 * Typed row classes for GTFS `shapes.txt`.
 *
 * `GtfsShape` is a per-revision summary row (one per `shape_id`); `GtfsShapePoint` holds the
 * ordered geometry points. All coordinate / distance columns are `DOUBLE PRECISION`; counts and
 * sequences are `INT`.
 */
class Shape(
    revisionId: Long,
    var shapeId: String,
    var pointCount: Int,
    // Trailing single capital: CamelCaseToUnderscoresNamingStrategy yields "lengthm", so name it
    // explicitly.
    var lengthM: Double?,
    /** Display geometry: GeoJSON LineString, gzip + base64 (see [compactLineGeometry]). Null for older revisions. */
    var geometry: String? = null,
) : RevisionScoped(revisionId)

/**
 * Encodes ordered `(lat, lon)` points as a GeoJSON `LineString` (`[lng, lat]`), gzip-compressed then
 * base64-encoded. Points with missing or out-of-range coordinates are skipped; null if fewer than two
 * valid points remain.
 */
fun compactLineGeometry(latLon: List<Pair<Double?, Double?>>): String? {
    val positions =
        latLon.mapNotNull { (lat, lon) ->
            if (lat == null || lon == null || lon !in -180.0..180.0 || lat !in -90.0..90.0) null else Position(lon, lat)
        }
    return if (positions.size >= 2) Line(positions).toGeoJsonBase64() else null
}

class ShapePoint(
    revisionId: Long,
    var shapeId: String,
    var shapePtLat: Double?,
    var shapePtLon: Double?,
    var shapePtSequence: Int,
    var shapeDistTraveled: Double?,
) : RevisionScoped(revisionId)

object ShapeTable : RevisionScopedTable<Shape>("shapes", Shape::class.java, "fk_shape_rev") {
    val shapeId = varchar("shape_id", 255)
    val pointCount = integer("point_count")
    val lengthM = double("length_m").nullable()
    val geometry = text("geometry").nullable()

    init {
        uniqueIndex("uq_shape_rev_id", revisionId, shapeId)
    }

    override fun read(row: ResultRow) =
        Shape(
            revisionId = row[revisionId],
            shapeId = row[shapeId],
            pointCount = row[pointCount],
            lengthM = row[lengthM],
            geometry = row[geometry],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Shape,
    ) {
        s[revisionId] = row.revisionId
        s[shapeId] = row.shapeId
        s[pointCount] = row.pointCount
        s[lengthM] = row.lengthM
        s[geometry] = row.geometry
    }
}

object ShapePointTable : RevisionScopedTable<ShapePoint>("shape_points", ShapePoint::class.java, "fk_shapept_rev") {
    val shapeId = varchar("shape_id", 255)
    val shapePtLat = double("shape_pt_lat").nullable()
    val shapePtLon = double("shape_pt_lon").nullable()
    val shapePtSequence = integer("shape_pt_sequence")
    val shapeDistTraveled = double("shape_dist_traveled").nullable()

    init {
        uniqueIndex("uq_shapept_rev_id_seq", revisionId, shapeId, shapePtSequence)
        index("ix_shapept_rev_shape_seq", false, revisionId, shapeId, shapePtSequence)
    }

    override fun read(row: ResultRow) =
        ShapePoint(
            revisionId = row[revisionId],
            shapeId = row[shapeId],
            shapePtLat = row[shapePtLat],
            shapePtLon = row[shapePtLon],
            shapePtSequence = row[shapePtSequence],
            shapeDistTraveled = row[shapeDistTraveled],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: ShapePoint,
    ) {
        s[revisionId] = row.revisionId
        s[shapeId] = row.shapeId
        s[shapePtLat] = row.shapePtLat
        s[shapePtLon] = row.shapePtLon
        s[shapePtSequence] = row.shapePtSequence
        s[shapeDistTraveled] = row.shapeDistTraveled
    }
}

@Repository
@Transactional
class ShapeRepository : RevisionScopedRepository<Shape>(ShapeTable) {
    fun findByShapeId(
        revisionId: Long,
        shapeId: String,
    ): Shape? =
        ShapeTable
            .selectAll()
            .where { (ShapeTable.revisionId eq revisionId) and (ShapeTable.shapeId eq shapeId) }
            .singleOrNull()
            ?.let(ShapeTable::read)
}

@Repository
@Transactional
class ShapePointRepository : RevisionScopedRepository<ShapePoint>(ShapePointTable) {
    fun findByShapeId(
        revisionId: Long,
        shapeId: String,
    ): List<ShapePoint> =
        ShapePointTable
            .selectAll()
            .where { (ShapePointTable.revisionId eq revisionId) and (ShapePointTable.shapeId eq shapeId) }
            .orderBy(ShapePointTable.shapePtSequence)
            .map(ShapePointTable::read)
}
