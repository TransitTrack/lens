package eu.transittrack.gtfs.api.dto

data class DraftDto(
    val id: String,
    val feedCode: String,
    val label: String?,
    val baseRevisionId: String?,
    val status: String,
    val kind: String,
    val version: Long,
    val derivationStale: Boolean,
    val lastValidation: Any?,
    val lock: DraftLockDto?,
    val createdBy: String?,
    val createdAt: String,
    val rowCounts: Map<String, Long>,
)

data class DraftJobDto(
    val id: String,
    val state: String,
    val phase: String,
    val error: String?,
)

data class DraftLockDto(
    val editor: String,
    val expiresAt: String,
)

data class DraftEditDto(
    val seq: Int,
    val op: String,
    val summary: String,
    val appliedAt: String,
    val undone: Boolean,
)

data class ForkDraftInput(
    val feedCode: String,
    val baseRevisionId: String?,
    val label: String?,
    val editor: String,
)
