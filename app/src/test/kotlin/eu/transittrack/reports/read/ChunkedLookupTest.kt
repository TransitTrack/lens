package eu.transittrack.reports.read

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo

/**
 * Pure unit test for the chunking pattern used in [StopPassageDerivationService.derive] to avoid
 * exceeding Postgres's bind-parameter limit on `findAllById`:
 * ```
 * ids.distinct().chunked(20_000).flatMap { repo.findAllById(it) }.associateBy { it.id }
 * ```
 * A real repository/Postgres round trip isn't exercised here (that's what
 * [StopPassageDerivationServiceTest] covers against a real feed) - this only proves the chunking
 * + merging logic itself is correct: chunking a large/duplicate-heavy id list and merging the
 * per-chunk lookups produces exactly the same result as one unchunked lookup over all ids, even
 * when the same id would otherwise land in more than one chunk.
 */
class ChunkedLookupTest {
    private data class Row(
        val id: Long,
        val value: String,
    )

    /** Stand-in for a `findAllById` repository call: returns the subset of [all] whose id is requested. */
    private fun fakeFindAllById(
        all: List<Row>,
        ids: Collection<Long>,
    ): List<Row> = all.filter { it.id in ids }

    private fun chunkedLookup(
        all: List<Row>,
        ids: List<Long>,
        chunkSize: Int,
    ): Map<Long, Row> =
        ids
            .distinct()
            .chunked(chunkSize)
            .flatMap { fakeFindAllById(all, it) }
            .associateBy { it.id }

    @Test
    fun `chunked lookup over duplicate-heavy ids matches one unchunked lookup`() {
        val all = (1L..37L).map { Row(it, "row-$it") }
        // Deliberately duplicate-heavy and unordered, so ids repeat across chunk boundaries for a
        // small chunk size (5) the way avlReportId repeats across the full (20_000-sized) chunks in
        // production when many VehicleMatch rows point at the same AvlReportRow.
        val ids = (1L..37L).flatMap { listOf(it, it, it) }.shuffled(kotlin.random.Random(42))

        val chunked = chunkedLookup(all, ids, chunkSize = 5)
        val unchunked = fakeFindAllById(all, ids.distinct()).associateBy { it.id }

        assertThat(chunked).isEqualTo(unchunked)
        assertThat(chunked.keys.size).isEqualTo(37)
    }

    @Test
    fun `chunked lookup handles an id list smaller than one chunk`() {
        val all = listOf(Row(1L, "a"), Row(2L, "b"), Row(3L, "c"))
        val ids = listOf(1L, 2L, 1L)

        val chunked = chunkedLookup(all, ids, chunkSize = 20_000)

        assertThat(chunked).isEqualTo(mapOf(1L to Row(1L, "a"), 2L to Row(2L, "b")))
    }

    @Test
    fun `chunked lookup over an id list exactly at a chunk boundary does not drop or duplicate rows`() {
        val all = (1L..10L).map { Row(it, "row-$it") }
        val ids = (1L..10L).toList()

        // chunkSize 5 splits [1..10] into exactly two full chunks - the boundary case most likely
        // to silently drop the first id of the second chunk if the chunking were off by one.
        val chunked = chunkedLookup(all, ids, chunkSize = 5)

        assertThat(chunked).isEqualTo(all.associateBy { it.id })
    }
}
