package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.group.ManageSeatHoldGroups
import de.holisticon.cinema.booking.application.port.SeatHoldGroup
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Validator
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/showings/{showingId}")
class SeatHoldGroupController(private val groups: ManageSeatHoldGroups, private val validator: Validator) {
    @Operation(
        operationId = "reserveSeatGroup",
        summary = "Hold every selected seat together for five minutes",
        responses = [
            ApiResponse(responseCode = "201", description = "All selected seats are held.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = SeatHoldGroupResponse::class))]),
            ApiResponse(responseCode = "400", description = "The showing ID or seat selection is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "404", description = "The showing or a selected seat does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "At least one selected seat is held or booked.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(@PathVariable showingId: Long, @RequestBody request: ReserveSeatGroupRequest): SeatHoldGroupResponse =
        groups.reserve(showingId, request.seatIds).toResponse()

    @Operation(
        operationId = "getSeatGroupHold",
        summary = "Resume a live hold or recover a confirmed booking receipt",
        responses = [
            ApiResponse(responseCode = "200", description = "The live hold or confirmed booking receipt.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = SeatHoldGroupResponse::class))]),
            ApiResponse(responseCode = "400", description = "The showing ID or hold credential is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "404", description = "The showing or hold does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The hold expired or was released.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/holds/status")
    fun status(@PathVariable showingId: Long, @RequestBody request: SeatHoldCredentialRequest): SeatHoldGroupResponse =
        groups.status(showingId, request.holdId).toResponse()

    @Operation(
        operationId = "releaseSeatGroup",
        summary = "Release the seats still held by a group",
        responses = [
            ApiResponse(responseCode = "204", description = "The group is released. Repeat requests are safe.", content = [Content()]),
            ApiResponse(responseCode = "400", description = "The showing ID or hold credential is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "404", description = "The showing or hold does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The group has already been booked.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/holds/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun release(@PathVariable showingId: Long, @RequestBody request: SeatHoldCredentialRequest) {
        groups.release(showingId, request.holdId)
    }

    @Operation(
        operationId = "confirmSeatGroupBooking",
        summary = "Confirm every seat in a held group",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(ref = CONFIRM_BOOKING_SCHEMA_REF))],
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "The group was already confirmed. Returns its original booking ID.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = BookingResponse::class))]),
            ApiResponse(responseCode = "201", description = "The group booking was created.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = BookingResponse::class))]),
            ApiResponse(responseCode = "400", description = "The path or booking request is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "404", description = "The showing or hold does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The hold expired, was released, or is unavailable.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/bookings")
    fun book(@PathVariable showingId: Long, @RequestBody request: ConfirmBookingRequest): ResponseEntity<BookingResponse> {
        val customer = request.validatedCustomerDetails(validator)
        return groups.confirm(showingId, request.holdId, customer).toHttpResponse()
    }
}

data class SeatHoldGroupResponse(
    val holdId: UUID,
    val seatIds: List<Long>,
    val expiresAt: Instant,
    @get:Schema(types = ["string", "null"], format = "uuid")
    val bookingId: UUID?,
) {
    override fun toString(): String = "SeatHoldGroupResponse"
}

private fun SeatHoldGroup.toResponse(): SeatHoldGroupResponse =
    SeatHoldGroupResponse(holdId, members.map { it.seatId }, expiresAt, bookingId)
