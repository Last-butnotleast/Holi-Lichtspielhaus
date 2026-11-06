package de.holisticon.cinema.booking.application.query

import de.holisticon.cinema.booking.application.port.SeatMapReader
import de.holisticon.cinema.booking.domain.isHoldActive
import java.time.Clock
import org.springframework.stereotype.Service

@Service
class GetSeatMap(private val reader: SeatMapReader, private val clock: Clock) {
    fun get(showingId: Long): SeatMapView {
        val seats = reader.findSeats(showingId) ?: throw ShowingNotFound(showingId)
        val now = clock.instant()
        return SeatMapView(
            showingId,
            seats.map { seat ->
                val status = when {
                    seat.booked -> SeatStatus.BOOKED
                    isHoldActive(seat.expiresAt, now) -> SeatStatus.HELD
                    else -> SeatStatus.AVAILABLE
                }
                SeatView(seat.seatId, status)
            },
        )
    }
}

class ShowingNotFound(showingId: Long) : RuntimeException("Showing $showingId does not exist.")
