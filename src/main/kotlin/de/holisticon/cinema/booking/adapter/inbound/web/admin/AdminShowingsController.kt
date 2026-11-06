package de.holisticon.cinema.booking.adapter.inbound.web.admin

import com.fasterxml.jackson.annotation.JsonCreator
import de.holisticon.cinema.booking.adapter.inbound.web.ADMIN_BEARER_SECURITY
import de.holisticon.cinema.booking.adapter.inbound.web.ADMIN_UNAUTHORIZED_RESPONSE
import de.holisticon.cinema.booking.application.admin.ManageShowings
import de.holisticon.cinema.booking.application.admin.ShowingHasAllocations
import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.application.query.ShowingView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
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
@SecurityRequirement(name = ADMIN_BEARER_SECURITY)
class AdminShowingsController(private val manageShowings: ManageShowings) {
    @Operation(
        operationId = "createShowing",
        summary = "Create a showing and its 200 seats",
        responses = [
            ApiResponse(responseCode = "201", description = "The showing was created.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = ShowingView::class))]),
            ApiResponse(responseCode = "400", description = "The showing request is invalid.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "401", ref = ADMIN_UNAUTHORIZED_RESPONSE),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ShowingRequest): ShowingView =
        manageShowings.create(request.movieTitle, request.startsAt)

    @Operation(
        operationId = "updateShowing",
        summary = "Update a showing without active holds or bookings",
        responses = [
            ApiResponse(responseCode = "200", description = "The showing was updated.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = ShowingView::class))]),
            ApiResponse(responseCode = "400", description = "The path or showing request is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "401", ref = ADMIN_UNAUTHORIZED_RESPONSE),
            ApiResponse(responseCode = "404", description = "The showing does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The showing has active holds or confirmed bookings.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PutMapping("/{showingId}")
    fun update(@PathVariable showingId: Long, @RequestBody request: ShowingRequest): ShowingView =
        manageShowings.update(showingId, request.movieTitle, request.startsAt)

    @Operation(
        operationId = "deleteShowing",
        summary = "Delete a showing without active holds or bookings",
        responses = [
            ApiResponse(responseCode = "204", description = "The showing was deleted.", content = [Content()]),
            ApiResponse(responseCode = "400", description = "The showing ID is invalid.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object"))]),
            ApiResponse(responseCode = "401", ref = ADMIN_UNAUTHORIZED_RESPONSE),
            ApiResponse(responseCode = "404", description = "The showing does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The showing has active holds or confirmed bookings.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @DeleteMapping("/{showingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable showingId: Long) {
        manageShowings.delete(showingId)
    }
}

@Schema(requiredProperties = ["movieTitle", "startsAt"])
class ShowingRequest private constructor(
    @get:Schema(minLength = 1) val movieTitle: String,
    @get:Schema(type = "string", format = "date-time", description = "Start time with an explicit offset.")
    val startsAt: Instant,
) {
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
