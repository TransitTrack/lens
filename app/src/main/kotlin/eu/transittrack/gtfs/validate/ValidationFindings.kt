package eu.transittrack.gtfs.validate

import io.github.transittrack.gtfsanalyzer.Notice

/** The analyzer's own five severity levels (the API's [Severity] collapses them to three). */
enum class FindingSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
}

/**
 * One analyzer notice as exposed over GraphQL: what fired, on which entity and file row, what was
 * observed versus expected, and how to fix it. Unlike [ValidationIssue] this is not rolled up per rule.
 */
data class ValidationFinding(
    val ruleId: String,
    val title: String,
    val severity: FindingSeverity,
    val ruleClass: String,
    val entityType: String,
    val entityId: String?,
    val serviceId: String?,
    val file: String?,
    val line: Int?,
    val field: String?,
    val observedValue: String?,
    val expectedValue: String?,
    val details: Map<String, String>?,
    val message: String,
    val remediation: String,
)

/** How many findings one entity has, and the most severe of them. */
data class EntityFindingCount(
    val entityId: String,
    val count: Int,
    val worstSeverity: FindingSeverity,
)

/** Per-entity finding counts for one entity type, for list views that filter to entities with problems. */
fun List<Notice>.countsByEntity(entityType: String): List<EntityFindingCount> =
    filter { it.entityType.name.equals(entityType, ignoreCase = true) && it.entityId != null }
        .groupBy { it.entityId!! }
        .map { (id, notices) ->
            EntityFindingCount(
                entityId = id,
                count = notices.size,
                worstSeverity = FindingSeverity.valueOf(notices.minBy { it.severity.ordinal }.severity.name),
            )
        }.sortedBy { it.entityId }

/**
 * Notices for one entity (`entityType` matched case-insensitively, `entityId` exactly; a null filter
 * matches everything), most severe first, then by rule and file row.
 */
fun List<Notice>.toFindings(
    entityType: String?,
    entityId: String?,
): List<ValidationFinding> =
    filter { entityType == null || it.entityType.name.equals(entityType, ignoreCase = true) }
        .filter { entityId == null || it.entityId == entityId }
        .sortedWith(compareBy<Notice>({ it.severity.ordinal }, { it.ruleId }, { it.line ?: Long.MAX_VALUE }))
        .map {
            ValidationFinding(
                ruleId = it.ruleId,
                title = it.title,
                severity = FindingSeverity.valueOf(it.severity.name),
                ruleClass = it.ruleClass.name,
                entityType = it.entityType.name,
                entityId = it.entityId,
                serviceId = it.serviceId,
                file = it.file,
                line = it.line?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                field = it.field,
                observedValue = it.observedValue,
                expectedValue = it.expectedValue,
                details = it.details,
                message = it.message,
                remediation = it.remediation,
            )
        }
