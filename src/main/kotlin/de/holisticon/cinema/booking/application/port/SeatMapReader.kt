package de.holisticon.cinema.booking.application.port

import java.time.Instant

interface SeatMapReader {
    fun findSeats(showingId: Long): List<SeatHoldSnapshot>?
}

data class SeatHoldSnapshot(val seatId: Long, val expiresAt: Instant?)
