package eu.transittrack.avl.feed

import java.time.Instant

class RawAvlPayload(
    val bytes: ByteArray,
    val contentType: String?,
    val fetchedAt: Instant,
)
