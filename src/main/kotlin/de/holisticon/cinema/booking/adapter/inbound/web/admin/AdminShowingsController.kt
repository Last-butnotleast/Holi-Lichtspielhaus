package de.holisticon.cinema.booking.adapter.inbound.web.admin

import com.fasterxml.jackson.annotation.JsonCreator
import de.holisticon.cinema.booking.application.admin.ManageShowings
import de.holisticon.cinema.booking.application.admin.ShowingHasAllocations
import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.application.query.ShowingView
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.JsonNode

@RestController
@RequestMapping("/admin/showings")
class AdminShowingsController(private val manageShowings: ManageShowings) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ShowingRequest): ShowingView =
        manageShowings.create(request.movieTitle, request.startsAt)

    @PutMapping("/{showingId}")
    fun update(@PathVariable showingId: Long, @RequestBody request: ShowingRequest): ShowingView =
        manageShowings.update(showingId, request.movieTitle, request.startsAt)

    @DeleteMapping("/{showingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable showingId: Long) {
        manageShowings.delete(showingId)
    }
}

class ShowingRequest private constructor(val movieTitle: String, val startsAt: Instant) {
    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(body: JsonNode): ShowingRequest {
            if (!body.isObject) throw InvalidShowingRequest()

            val title = body.requiredText("movieTitle").trim()
            if (title.isBlank() || '\u0000' in title) throw InvalidShowingRequest()
            val startsAt = try {
                OffsetDateTime.parse(body.requiredText("startsAt"))
            } catch (_: DateTimeParseException) {
                throw InvalidShowingRequest()
            }
            if (startsAt.year !in 1..9999) throw InvalidShowingRequest()

            return ShowingRequest(title, startsAt.toInstant())
        }

        private fun JsonNode.requiredText(field: String): String =
            get(field)?.takeIf { it.isString }?.stringValue() ?: throw InvalidShowingRequest()
    }
}

class InvalidShowingRequest : RuntimeException()

@RestControllerAdvice(assignableTypes = [AdminShowingsController::class])
class AdminShowingsErrors {
    @ExceptionHandler(InvalidShowingRequest::class, HttpMessageNotReadableException::class)
    fun invalidRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A movie title and offset-aware start time are required.")

    @ExceptionHandler(ShowingNotFound::class)
    fun notFound(error: ShowingNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(ShowingHasAllocations::class)
    fun conflict(error: ShowingHasAllocations): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
