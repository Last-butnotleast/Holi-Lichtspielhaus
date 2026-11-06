package de.holisticon.cinema.booking.application.query

import de.holisticon.cinema.booking.application.port.SeatMapReader
import de.holisticon.cinema.booking.domain.isHoldActive
import java.time.Clock
import org.springframework.stereotype.Service

private const val MAX_READ_ATTEMPTS = 3

@Service
class GetSeatMap(private val reader: SeatMapReader, private val clock: Clock) {
    fun get(showingId: Long): SeatMapView {
        repeat(MAX_READ_ATTEMPTS) {
            val before = clock.instant()
            val seats = reader.findSeats(showingId) ?: throw ShowingNotFound(showingId)
            val after = clock.instant()
            val holdExpiredDuringRead = seats.any { seat ->
                !seat.booked && isHoldActive(seat.expiresAt, before) && !isHoldActive(seat.expiresAt, after)
            }
            if (after < before || holdExpiredDuringRead) return@repeat

            return SeatMapView(
                showingId,
                seats.map { seat ->
                    val status = when {
                        seat.booked -> SeatStatus.BOOKED
                        isHoldActive(seat.expiresAt, after) -> SeatStatus.HELD
                        else -> SeatStatus.AVAILABLE
                    }
                    SeatView(seat.seatId, status)
                },
            )
        }
        throw SeatMapTemporarilyUnavailable(showingId)
    }
}

class ShowingNotFound(showingId: Long) : RuntimeException("Showing $showingId does not exist.")

class SeatMapTemporarilyUnavailable(showingId: Long) :
    RuntimeException("Seat map for showing $showingId is temporarily unavailable.")
