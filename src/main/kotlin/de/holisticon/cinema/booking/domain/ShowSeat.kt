package de.holisticon.cinema.booking.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

private val HOLD_DURATION = Duration.ofMinutes(5)

class ShowSeat(
    val showingId: Long,
    val seatId: Long,
    hold: SeatHold?,
) {
    var hold: SeatHold? = hold
        private set

    fun reserve(holdId: UUID, now: Instant): SeatHold {
        if (isHoldActive(hold?.expiresAt, now)) {
            throw SeatAlreadyHeld(showingId, seatId)
        }

        val newHold = SeatHold(holdId, now.plus(HOLD_DURATION))
        hold = newHold
        return newHold
    }
}

class SeatAlreadyHeld(showingId: Long, seatId: Long) :
    RuntimeException("Seat $seatId in showing $showingId already has an active hold.")
