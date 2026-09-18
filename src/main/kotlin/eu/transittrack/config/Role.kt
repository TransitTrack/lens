package eu.transittrack.config

enum class Role(
    val profile: String,
) {
    API("role-api"),
    INGESTER("role-ingester"),
    FEED_PROCESSOR("role-feed-processor"),
    PREDICTOR("role-predictor"),
}
