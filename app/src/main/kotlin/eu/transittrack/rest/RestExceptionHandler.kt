package eu.transittrack.rest

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

import eu.transittrack.ConditionalOnRole
import eu.transittrack.Role
import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException
import eu.transittrack.gtfs.feed.FeedProtectedException
import eu.transittrack.rest.generated.model.Problem

/**
 * Maps the domain's "you asked for something wrong" exceptions to problem responses for every REST
 * controller, so a client sees the reason instead of a generic 500. Controllers with their own
 * `@ExceptionHandler` for an exception keep precedence.
 */
@RestControllerAdvice
@ConditionalOnRole(Role.API)
class RestExceptionHandler {
    @ExceptionHandler(FeedNotFoundException::class)
    fun notFound(ex: FeedNotFoundException) = problem(HttpStatus.NOT_FOUND, "Not Found", ex)

    @ExceptionHandler(FeedConflictException::class, FeedProtectedException::class)
    fun conflict(ex: RuntimeException) = problem(HttpStatus.CONFLICT, "Conflict", ex)

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun badRequest(ex: RuntimeException) = problem(HttpStatus.BAD_REQUEST, "Bad Request", ex)

    private fun problem(
        status: HttpStatus,
        title: String,
        ex: RuntimeException,
    ): ResponseEntity<Problem> =
        ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(Problem(title = title, status = status.value(), detail = ex.message))
}
