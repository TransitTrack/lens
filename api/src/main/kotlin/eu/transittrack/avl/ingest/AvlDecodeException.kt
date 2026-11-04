package eu.transittrack.avl.ingest

class AvlDecodeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

