package eu.transittrack.gtfs.validate

import io.github.transittrack.gtfsanalyzer.Notice

import eu.transittrack.rest.generated.model.EntityFindingCount
import eu.transittrack.rest.generated.model.FindingSeverity
import eu.transittrack.rest.generated.model.ValidationFinding

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
