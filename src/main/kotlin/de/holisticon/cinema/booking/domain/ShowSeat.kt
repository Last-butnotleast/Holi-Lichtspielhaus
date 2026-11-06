package de.holisticon.cinema.booking.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

private val HOLD_DURATION = Duration.ofMinutes(5)

class ShowSeat(
    val showingId: Long,
    val seatId: Long,
    hold: SeatHold?,
    booking: ConfirmedBooking? = null,
) {
    var hold: SeatHold? = hold
        private set

    var booking: ConfirmedBooking? = booking
        private set

    fun reserve(holdId: UUID, now: Instant): SeatHold {
        if (booking != null) {
            throw SeatAlreadyBooked(showingId, seatId)
        }
        if (isHoldActive(hold?.expiresAt, now)) {
            throw SeatAlreadyHeld(showingId, seatId)
        }

        val newHold = SeatHold(holdId, now.plus(HOLD_DURATION))
        hold = newHold
        return newHold
    }

    fun confirm(holdId: UUID, bookingId: UUID, customer: CustomerDetails, now: Instant): BookingConfirmation {
        booking?.let { confirmed ->
            if (confirmed.holdId == holdId) {
                return BookingConfirmation.Replayed(confirmed.id)
            }
            throw SeatAlreadyBooked(showingId, seatId)
        }

        val currentHold = hold
        if (currentHold?.id != holdId || !isHoldActive(currentHold.expiresAt, now)) {
            throw HoldNotConfirmable(showingId, seatId)
        }

        booking = ConfirmedBooking(bookingId, holdId, now, customer)
        hold = null
        return BookingConfirmation.Created(bookingId)
    }
}

class SeatAlreadyHeld(showingId: Long, seatId: Long) :
    RuntimeException("Seat $seatId in showing $showingId already has an active hold.")

class SeatAlreadyBooked(showingId: Long, seatId: Long) :
    RuntimeException("Seat $seatId in showing $showingId is booked.")

class HoldNotConfirmable(showingId: Long, seatId: Long) :
    RuntimeException("The supplied hold is not active for seat $seatId in showing $showingId.")
