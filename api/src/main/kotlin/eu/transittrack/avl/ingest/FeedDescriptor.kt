package eu.transittrack.avl.ingest

data class FeedDescriptor(
    val code: String,
    val name: String,
    val url: String,
    val format: AvlFormat,
    val headers: Map<String, String>?,
)
