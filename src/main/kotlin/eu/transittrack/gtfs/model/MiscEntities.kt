package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Typed JPA entities for the remaining GTFS files: transfers.txt,
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
@Table(name = "transfers")
class Transfer(
    revisionId: Long,
    var fromStopId: String?,
    var toStopId: String?,
    var fromRouteId: String?,
    var toRouteId: String?,
    var fromTripId: String?,
    var toTripId: String?,
    @JdbcTypeCode(SqlTypes.SMALLINT) var transferType: Int?,
    var minTransferTime: Int?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "translations")
class Translation(
    revisionId: Long,
    @Column(name = "table_name", nullable = false) var tableName: String,
    @Column(name = "field_name", nullable = false) var fieldName: String,
    @Column(name = "language", nullable = false) var language: String,
    var translation: String?,
    var recordId: String?,
    var recordSubId: String?,
    var fieldValue: String?,
) : RevisionScoped(revisionId)

@Entity
@Table(name = "attributions")
class Attribution(
    revisionId: Long,
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
) : RevisionScoped(revisionId)

interface TransferRepository : RevisionScopedRepository<Transfer, Long>

interface TranslationRepository : RevisionScopedRepository<Translation, Long>

interface AttributionRepository : RevisionScopedRepository<Attribution, Long>
