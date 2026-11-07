package eu.transittrack.rest

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException

/** Maps one row to its model in a 200, or answers 404 when [row] is absent. */
fun <T : Any, E : Any> found(
    row: E?,
    map: (E) -> T,
): ResponseEntity<T> = ResponseEntity.ok(map(row ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")))

/**
 * Validates paging, then maps one page of [rows] (as a batch, so lookups can be shared) with an
 * `X-Total-Count` header carrying the unpaged size.
 */
fun <T : Any, E : Any> pageOf(
    offset: Int,
    limit: Int,
    rows: () -> List<E>,
    map: (List<E>) -> List<T>,
): ResponseEntity<List<T>> {
    if (offset < 0 || limit !in 1..500) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination")
    val all = rows()
    return ResponseEntity
        .ok()
        .header("X-Total-Count", all.size.toString())
        .body(map(all.drop(offset).take(limit)))
}
