package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.query.GetSeatMap
import de.holisticon.cinema.booking.application.query.SeatMapView
import de.holisticon.cinema.booking.application.reserve.ReserveSeat
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/showings/{showingId}/seats")
class BookingController(private val reserveSeat: ReserveSeat, private val getSeatMap: GetSeatMap) {
    @PostMapping("/{seatId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    fun hold(@PathVariable showingId: Long, @PathVariable seatId: Long): SeatHoldResponse {
        val hold = reserveSeat.reserve(showingId, seatId)
        return SeatHoldResponse(hold.id, hold.expiresAt)
    }

    @GetMapping
    fun seatMap(@PathVariable showingId: Long): SeatMapView = getSeatMap.get(showingId)
}

data class SeatHoldResponse(val holdId: UUID, val expiresAt: Instant)
