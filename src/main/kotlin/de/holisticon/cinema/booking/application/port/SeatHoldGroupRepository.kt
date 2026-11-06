package de.holisticon.cinema.booking.application.port

import java.time.Instant
import java.util.UUID

interface SeatHoldGroupRepository {
    fun insert(showingId: Long, group: SeatHoldGroup)

    fun findForUpdate(showingId: Long, holdId: UUID): SeatHoldGroup?

    fun markReleased(holdId: UUID, releasedAt: Instant)

    fun markConfirmed(holdId: UUID, bookingId: UUID)

    fun isMemberHold(holdId: UUID): Boolean
}

class SeatHoldGroup(
    val holdId: UUID,
    val expiresAt: Instant,
    val members: List<SeatHoldGroupMember>,
    val releasedAt: Instant? = null,
    val bookingId: UUID? = null,
)

class SeatHoldGroupMember(val seatId: Long, val holdId: UUID)
