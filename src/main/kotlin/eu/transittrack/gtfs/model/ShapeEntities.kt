package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Typed JPA entities for GTFS `shapes.txt`.
 *
 * `GtfsShape` is a per-revision summary row (one per `shape_id`); `GtfsShapePoint`
 * holds the ordered geometry points. All coordinate / distance columns are
 * `DOUBLE PRECISION`; counts and sequences are `INT`.
 */

@Entity
@Table(name = "gtfs_shape")
class GtfsShape(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "shape_id", nullable = false) var shapeId: String,
    @Column(name = "point_count", nullable = false) var pointCount: Int,
    // Trailing single capital: CamelCaseToUnderscoresNamingStrategy yields "lengthm", so name it explicitly.
    @Column(name = "length_m") var lengthM: Double?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_shape_point")
class GtfsShapePoint(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "shape_id", nullable = false) var shapeId: String,
    var shapePtLat: Double?,
    var shapePtLon: Double?,
    @Column(name = "shape_pt_sequence", nullable = false) var shapePtSequence: Int,
    var shapeDistTraveled: Double?,
) : RevisionScoped()

interface GtfsShapeRepository : JpaRepository<GtfsShape, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsShape>
    fun findByRevisionIdAndShapeId(revisionId: Long, shapeId: String): GtfsShape?
}

interface GtfsShapePointRepository : JpaRepository<GtfsShapePoint, Long> {
    fun findByRevisionIdAndShapeIdOrderByShapePtSequence(revisionId: Long, shapeId: String): List<GtfsShapePoint>
}
