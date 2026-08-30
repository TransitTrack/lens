package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Typed JPA entities for the remaining GTFS files: frequencies.txt, transfers.txt,
 * translations.txt, attributions.txt.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields
 * carry `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate`
 * sees matching JDBC type codes. `gtfs_frequency.start_time` / `end_time` /
 * `headway_secs` are stored as seconds (`INT`), and `gtfs_transfer.min_transfer_time`
 * is stored as seconds (`INT`).
 *
 * `is_producer` / `is_operator` / `is_authority` are non-boolean GTFS enum ints, so
 * they carry explicit `@Column(name = ...)` to avoid Kotlin's `is`-prefix property
 * naming quirk.
 */

@Entity
@Table(name = "gtfs_frequency")
class GtfsFrequency(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "trip_id", nullable = false) var tripId: String,
    @Column(name = "start_time", nullable = false) var startTime: Int,
    var endTime: Int?,
    var headwaySecs: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var exactTimes: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_transfer")
class GtfsTransfer(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var fromStopId: String?,
    var toStopId: String?,
    var fromRouteId: String?,
    var toRouteId: String?,
    var fromTripId: String?,
    var toTripId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var transferType: Int?,
    var minTransferTime: Int?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_translation")
class GtfsTranslation(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    @Column(name = "table_name", nullable = false) var tableName: String,
    @Column(name = "field_name", nullable = false) var fieldName: String,
    @Column(name = "language", nullable = false) var language: String,
    var translation: String?,
    var recordId: String?,
    var recordSubId: String?,
    var fieldValue: String?,
) : RevisionScoped()

@Entity
@Table(name = "gtfs_attribution")
class GtfsAttribution(
    @Column(name = "revision_id", nullable = false) var revisionId: Long,
    var attributionId: String?,
    var agencyId: String?,
    var routeId: String?,
    var tripId: String?,
    @Column(name = "organization_name", nullable = false) var organizationName: String,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "is_producer") var isProducer: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "is_operator") var isOperator: Int?,
    @JdbcTypeCode(SqlTypes.SMALLINT) @Column(name = "is_authority") var isAuthority: Int?,
    var attributionUrl: String?,
    var attributionEmail: String?,
    var attributionPhone: String?,
) : RevisionScoped()

interface GtfsFrequencyRepository : JpaRepository<GtfsFrequency, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsFrequency>
    fun findByRevisionIdAndTripId(revisionId: Long, tripId: String): List<GtfsFrequency>
}

interface GtfsTransferRepository : JpaRepository<GtfsTransfer, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsTransfer>
}

interface GtfsTranslationRepository : JpaRepository<GtfsTranslation, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsTranslation>
}

interface GtfsAttributionRepository : JpaRepository<GtfsAttribution, Long> {
    fun findByRevisionId(revisionId: Long): List<GtfsAttribution>
}
