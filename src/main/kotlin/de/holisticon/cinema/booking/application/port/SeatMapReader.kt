package de.holisticon.cinema.booking.application.port

import java.time.Instant

interface SeatMapReader {
    fun findSeats(showingId: Long): List<SeatAvailabilitySnapshot>?
}

data class SeatAvailabilitySnapshot(val seatId: Long, val expiresAt: Instant?, val booked: Boolean)
