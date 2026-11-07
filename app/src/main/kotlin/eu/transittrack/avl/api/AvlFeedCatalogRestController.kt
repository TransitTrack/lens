package eu.transittrack.avl.api

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.avl.read.AvlReadService
import eu.transittrack.rest.generated.api.AvlFeedCatalogApi
import eu.transittrack.rest.generated.model.AvlAssignmentMode
import eu.transittrack.rest.generated.model.AvlFeed
import eu.transittrack.rest.generated.model.AvlFormat
import eu.transittrack.rest.generated.model.PredictionAlgorithm
import eu.transittrack.rest.generated.model.Problem

@RestController
@RequestMapping("/api/v1")
@ConditionalOnRole(Role.API)
class AvlFeedCatalogRestController(
    private val read: AvlReadService,
) : AvlFeedCatalogApi {
    @ExceptionHandler(ResponseStatusException::class)
    fun handleInvalidRequest(ex: ResponseStatusException): ResponseEntity<Problem> =
        ResponseEntity
            .status(ex.statusCode)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = "Bad Request", status = ex.statusCode.value(), detail = ex.reason))

    override fun avlFeeds(
        offset: Int,
        limit: Int,
    ): ResponseEntity<List<AvlFeed>> {
        if (offset < 0 || limit !in 1..500) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offset or limit")
        }
        val feeds = read.avlFeeds()
        val result = feeds.drop(offset).take(limit)
        return ResponseEntity.ok().header("X-Total-Count", feeds.size.toString()).body(result)
    }
}
