package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.confirm.ConfirmBooking
import de.holisticon.cinema.booking.application.query.GetSeatMap
import de.holisticon.cinema.booking.application.query.SeatMapView
import de.holisticon.cinema.booking.application.reserve.ReserveSeat
import de.holisticon.cinema.booking.domain.BookingConfirmation
import jakarta.validation.Validator
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
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
    @PostMapping("/{seatId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(@PathVariable showingId: Long, @PathVariable seatId: Long): SeatHoldResponse {
        val hold = reserveSeat.reserve(showingId, seatId)
        return SeatHoldResponse(hold.id, hold.expiresAt)
    }

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

    @GetMapping
    fun seatMap(@PathVariable showingId: Long): SeatMapView = getSeatMap.get(showingId)
}

data class SeatHoldResponse(val holdId: UUID, val expiresAt: Instant) {
    override fun toString(): String = "SeatHoldResponse(expiresAt=$expiresAt)"
}

data class BookingResponse(val bookingId: UUID)
