package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.confirm.ConfirmBooking
import de.holisticon.cinema.booking.application.query.GetSeatMap
import de.holisticon.cinema.booking.application.query.SeatMapView
import de.holisticon.cinema.booking.application.reserve.ReserveSeat
import de.holisticon.cinema.booking.domain.BookingConfirmation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.SchemaProperty
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/showings/{showingId}/seats")
class BookingController(
    private val reserveSeat: ReserveSeat,
    private val confirmBooking: ConfirmBooking,
    private val getSeatMap: GetSeatMap,
    private val validator: Validator,
) {
    @Operation(
        operationId = "reserveSeat",
        summary = "Hold an available seat for five minutes",
        responses = [
            ApiResponse(
                responseCode = "201",
                description = "The seat is held. Keep the hold ID for booking confirmation.",
                content = [Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = SeatHoldResponse::class),
                )],
            ),
            ApiResponse(responseCode = "400", description = "The showing or seat ID is invalid.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object"))]),
            ApiResponse(responseCode = "404", description = "The showing or seat does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The seat is held or booked.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/{seatId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(@PathVariable showingId: Long, @PathVariable seatId: Long): SeatHoldResponse {
        val hold = reserveSeat.reserve(showingId, seatId)
        return SeatHoldResponse(hold.id, hold.expiresAt)
    }

    @Operation(
        operationId = "confirmBooking",
        summary = "Confirm a held seat",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = Schema(
                    type = "object",
                    requiredProperties = ["holdId", "name", "birthDate", "email", "termsAccepted", "privacyAccepted"],
                ),
                schemaProperties = [
                    SchemaProperty(name = "holdId", schema = Schema(type = "string", format = "uuid")),
                    SchemaProperty(name = "name", schema = Schema(type = "string", minLength = 1, maxLength = 200)),
                    SchemaProperty(name = "birthDate", schema = Schema(
                        type = "string",
                        format = "date",
                        description = "For a new booking, this date must be on or before the current date in Europe/Berlin.",
                    )),
                    SchemaProperty(name = "email", schema = Schema(type = "string", format = "email", maxLength = 254)),
                    SchemaProperty(name = "phone", schema = Schema(types = ["string", "null"], maxLength = 50)),
                    SchemaProperty(name = "termsAccepted", schema = Schema(type = "boolean", _const = "true")),
                    SchemaProperty(name = "privacyAccepted", schema = Schema(type = "boolean", _const = "true")),
                    SchemaProperty(name = "newsletterOptIn", schema = Schema(
                        types = ["boolean", "null"],
                        description = "Defaults to false when omitted or null.",
                    )),
                ],
            )],
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "The hold was already confirmed. Returns its original booking ID.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = BookingResponse::class))]),
            ApiResponse(responseCode = "201", description = "The booking was created.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = BookingResponse::class))]),
            ApiResponse(responseCode = "400", description = "The path or booking request is invalid.",
                content = [
                    Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object")),
                    Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class)),
                ]),
            ApiResponse(responseCode = "404", description = "The showing or seat does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "409", description = "The hold is invalid or expired, or the seat has another booking.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @PostMapping("/{seatId}/bookings")
    fun book(
        @PathVariable showingId: Long,
        @PathVariable seatId: Long,
        @RequestBody request: ConfirmBookingRequest,
    ): ResponseEntity<BookingResponse> {
        val customer = request.validatedCustomerDetails(validator)
        val confirmation = confirmBooking.confirm(showingId, seatId, request.holdId, customer)
        val status = when (confirmation) {
            is BookingConfirmation.Created -> HttpStatus.CREATED
            is BookingConfirmation.Replayed -> HttpStatus.OK
        }
        return ResponseEntity.status(status).body(BookingResponse(confirmation.bookingId))
    }

    @Operation(
        operationId = "getSeatMap",
        summary = "Get the availability of seats for a showing",
        responses = [
            ApiResponse(responseCode = "200", description = "The current seat map.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(implementation = SeatMapView::class))]),
            ApiResponse(responseCode = "400", description = "The showing ID is invalid.",
                content = [Content(mediaType = APPLICATION_JSON_VALUE, schema = Schema(type = "object"))]),
            ApiResponse(responseCode = "404", description = "The showing does not exist.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
            ApiResponse(responseCode = "503", description = "Seat availability is temporarily unavailable.",
                content = [Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = Schema(implementation = ProblemDetail::class))]),
        ],
    )
    @GetMapping
    fun seatMap(@PathVariable showingId: Long): SeatMapView = getSeatMap.get(showingId)
}

data class SeatHoldResponse(val holdId: UUID, val expiresAt: Instant) {
    override fun toString(): String = "SeatHoldResponse(expiresAt=$expiresAt)"
}

data class BookingResponse(val bookingId: UUID)
