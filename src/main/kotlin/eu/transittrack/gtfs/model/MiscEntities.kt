package eu.transittrack.gtfs.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.springframework.stereotype.Repository

import eu.transittrack.db.bridge.ExposedBridge

/**
 * Typed JPA entities for the remaining GTFS files: transfers.txt, translations.txt,
 * attributions.txt.
 *
 * GTFS enum integers are stored as `SMALLINT`; the matching Kotlin `Int?` fields carry
 * `@JdbcTypeCode(SqlTypes.SMALLINT)` so Hibernate `ddl-auto: validate` sees matching JDBC type
 * codes. `gtfs_frequency.start_time` / `end_time` / `headway_secs` are stored as seconds (`INT`),
 * and `gtfs_transfer.min_transfer_time` is stored as seconds (`INT`).
 *
 * `is_producer` / `is_operator` / `is_authority` are non-boolean GTFS enum ints, so they carry
 * explicit `@Column(name = ...)` to avoid Kotlin's `is`-prefix property naming quirk.
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

object TransferTable : RevisionScopedTable<Transfer>("transfers", Transfer::class.java, "fk_transfer_rev") {
    val fromStopId = varchar("from_stop_id", 255).nullable()
    val toStopId = varchar("to_stop_id", 255).nullable()
    val fromRouteId = varchar("from_route_id", 255).nullable()
    val toRouteId = varchar("to_route_id", 255).nullable()
    val fromTripId = varchar("from_trip_id", 255).nullable()
    val toTripId = varchar("to_trip_id", 255).nullable()
    val transferType = short("transfer_type").nullable()
    val minTransferTime = integer("min_transfer_time").nullable()

    init {
        index("ix_transfer_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        Transfer(
            revisionId = row[revisionId],
            fromStopId = row[fromStopId],
            toStopId = row[toStopId],
            fromRouteId = row[fromRouteId],
            toRouteId = row[toRouteId],
            fromTripId = row[fromTripId],
            toTripId = row[toTripId],
            transferType = row[transferType]?.toInt(),
            minTransferTime = row[minTransferTime],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Transfer,
    ) {
        s[revisionId] = row.revisionId
        s[fromStopId] = row.fromStopId
        s[toStopId] = row.toStopId
        s[fromRouteId] = row.fromRouteId
        s[toRouteId] = row.toRouteId
        s[fromTripId] = row.fromTripId
        s[toTripId] = row.toTripId
        s[transferType] = row.transferType?.toShort()
        s[minTransferTime] = row.minTransferTime
    }
}

object TranslationTable : RevisionScopedTable<Translation>("translations", Translation::class.java, "fk_translation_rev") {
    // `table_name`: the property can't be called `tableName`, which Table already declares.
    val translatedTable = varchar("table_name", 64)
    val fieldName = varchar("field_name", 64)
    val language = varchar("language", 16)
    val translation = text("translation").nullable()
    val recordId = varchar("record_id", 255).nullable()
    val recordSubId = varchar("record_sub_id", 255).nullable()
    val fieldValue = text("field_value").nullable()

    init {
        index("ix_translation_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        Translation(
            revisionId = row[revisionId],
            tableName = row[translatedTable],
            fieldName = row[fieldName],
            language = row[language],
            translation = row[translation],
            recordId = row[recordId],
            recordSubId = row[recordSubId],
            fieldValue = row[fieldValue],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Translation,
    ) {
        s[revisionId] = row.revisionId
        s[translatedTable] = row.tableName
        s[fieldName] = row.fieldName
        s[language] = row.language
        s[translation] = row.translation
        s[recordId] = row.recordId
        s[recordSubId] = row.recordSubId
        s[fieldValue] = row.fieldValue
    }
}

object AttributionTable : RevisionScopedTable<Attribution>("attributions", Attribution::class.java, "fk_attribution_rev") {
    val attributionId = varchar("attribution_id", 255).nullable()
    val agencyId = varchar("agency_id", 255).nullable()
    val routeId = varchar("route_id", 255).nullable()
    val tripId = varchar("trip_id", 255).nullable()
    val organizationName = varchar("organization_name", 255)
    val isProducer = short("is_producer").nullable()
    val isOperator = short("is_operator").nullable()
    val isAuthority = short("is_authority").nullable()
    val attributionUrl = text("attribution_url").nullable()
    val attributionEmail = varchar("attribution_email", 255).nullable()
    val attributionPhone = varchar("attribution_phone", 64).nullable()

    init {
        index("ix_attribution_rev", false, revisionId)
    }

    override fun read(row: ResultRow) =
        Attribution(
            revisionId = row[revisionId],
            attributionId = row[attributionId],
            agencyId = row[agencyId],
            routeId = row[routeId],
            tripId = row[tripId],
            organizationName = row[organizationName],
            isProducer = row[isProducer]?.toInt(),
            isOperator = row[isOperator]?.toInt(),
            isAuthority = row[isAuthority]?.toInt(),
            attributionUrl = row[attributionUrl],
            attributionEmail = row[attributionEmail],
            attributionPhone = row[attributionPhone],
        ).withId(row)

    override fun bind(
        s: UpdateBuilder<*>,
        row: Attribution,
    ) {
        s[revisionId] = row.revisionId
        s[attributionId] = row.attributionId
        s[agencyId] = row.agencyId
        s[routeId] = row.routeId
        s[tripId] = row.tripId
        s[organizationName] = row.organizationName
        s[isProducer] = row.isProducer?.toShort()
        s[isOperator] = row.isOperator?.toShort()
        s[isAuthority] = row.isAuthority?.toShort()
        s[attributionUrl] = row.attributionUrl
        s[attributionEmail] = row.attributionEmail
        s[attributionPhone] = row.attributionPhone
    }
}

@Repository
@ExposedBridge
class TransferRepository : RevisionScopedRepository<Transfer>(TransferTable)

@Repository
@ExposedBridge
class TranslationRepository : RevisionScopedRepository<Translation>(TranslationTable)

@Repository
@ExposedBridge
class AttributionRepository : RevisionScopedRepository<Attribution>(AttributionTable)
