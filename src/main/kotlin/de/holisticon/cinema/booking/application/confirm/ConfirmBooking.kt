package de.holisticon.cinema.booking.application.confirm

import de.holisticon.cinema.booking.application.SeatNotFound
import de.holisticon.cinema.booking.domain.BookingConfirmation
import de.holisticon.cinema.booking.domain.CustomerDetails
import de.holisticon.cinema.booking.domain.ShowSeatRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConfirmBooking(private val seats: ShowSeatRepository, private val clock: Clock) {
    @Transactional
    fun confirm(showingId: Long, seatId: Long, holdId: UUID, customer: CustomerDetails): BookingConfirmation {
        val seat = seats.findForUpdate(showingId, seatId) ?: throw SeatNotFound(showingId, seatId)
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val confirmation = seat.confirm(holdId, UUID.randomUUID(), customer, now)
        if (confirmation is BookingConfirmation.Created) {
            seats.save(seat)
        }
        return confirmation
    }
}
