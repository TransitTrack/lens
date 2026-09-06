package eu.transittrack.gtfs.revision

enum class GtfsRevisionStatus {
    PENDING,
    DOWNLOADING,
    VALIDATING,
    PARSING,
    DERIVING,
    READY,
    ACTIVE,
    SUPERSEDED,
    FAILED,
    UNCHANGED,
    DRAFT,
    ;

    val terminal: Boolean
        get() = this == ACTIVE || this == SUPERSEDED || this == FAILED || this == UNCHANGED

    companion object {
        val NON_TERMINAL_IN_PROGRESS = listOf(PENDING, DOWNLOADING, VALIDATING, PARSING, DERIVING)
    }
}
