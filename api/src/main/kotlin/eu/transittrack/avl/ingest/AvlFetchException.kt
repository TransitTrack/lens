package eu.transittrack.avl.ingest

class AvlFetchException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
