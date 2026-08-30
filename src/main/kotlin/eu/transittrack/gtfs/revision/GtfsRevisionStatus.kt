package eu.transittrack.gtfs.revision

enum class GtfsRevisionStatus {
    PENDING, DOWNLOADING, PARSING, VALIDATING, READY, ACTIVE, SUPERSEDED, FAILED, UNCHANGED;

    val terminal: Boolean
        get() = this == ACTIVE || this == SUPERSEDED || this == FAILED || this == UNCHANGED

    companion object {
        val NON_TERMINAL_IN_PROGRESS = listOf(PENDING, DOWNLOADING, PARSING, VALIDATING)
    }
}
