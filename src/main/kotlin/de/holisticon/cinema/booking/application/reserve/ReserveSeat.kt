package de.holisticon.cinema.booking.application.reserve

import de.holisticon.cinema.booking.application.SeatNotFound
import de.holisticon.cinema.booking.application.port.ShowingReservationLock
import de.holisticon.cinema.booking.domain.SeatHold
import de.holisticon.cinema.booking.domain.ShowSeatRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ReserveSeat(
    private val showings: ShowingReservationLock,
    private val seats: ShowSeatRepository,
    private val clock: Clock,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun reserve(showingId: Long, seatId: Long, expectedShowingRevision: Int): SeatHold {
        showings.requireRevision(showingId, expectedShowingRevision)
        val seat = seats.findForUpdate(showingId, seatId) ?: throw SeatNotFound(showingId, seatId)

        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        val hold = seat.reserve(UUID.randomUUID(), now)
        seats.save(seat)
        return hold
    }
}
