package eu.transittrack.util.paging

/** A page of [size] rows, [page] counting from 0 — the in-house replacement for Spring Data's `Pageable`. */
data class PageRequest(
    val page: Int,
    val size: Int,
) {
    init {
        require(page >= 0) { "page must not be negative" }
        require(size >= 1) { "size must be at least 1" }
    }

    /** Rows to skip before this page. */
    val offset: Long get() = page.toLong() * size
}

/** One page of [items] and the [total] number of rows across all pages. */
data class PageResult<T>(
    val items: List<T>,
    val total: Long,
)
