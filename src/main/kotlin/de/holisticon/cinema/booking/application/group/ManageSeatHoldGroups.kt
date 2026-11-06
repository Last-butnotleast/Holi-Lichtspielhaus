package de.holisticon.cinema.booking.application.group

import de.holisticon.cinema.booking.application.SeatNotFound
import de.holisticon.cinema.booking.application.port.SeatHoldGroup
import de.holisticon.cinema.booking.application.port.SeatHoldGroupMember
import de.holisticon.cinema.booking.application.port.SeatHoldGroupRepository
import de.holisticon.cinema.booking.application.port.ShowingReservationLock
import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.application.reserve.requireRevision
import de.holisticon.cinema.booking.domain.BookingConfirmation
import de.holisticon.cinema.booking.domain.CustomerDetails
import de.holisticon.cinema.booking.domain.ShowSeat
import de.holisticon.cinema.booking.domain.ShowSeatRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ManageSeatHoldGroups(
    private val showings: ShowingReservationLock,
    private val groups: SeatHoldGroupRepository,
    private val seats: ShowSeatRepository,
    private val clock: Clock,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun reserve(showingId: Long, seatIds: Set<Long>, expectedShowingRevision: Int): SeatHoldGroup {
        showings.requireRevision(showingId, expectedShowingRevision)
        val lockedSeats = seatIds.sorted().map { seatId -> lockSeat(showingId, seatId) }
        val now = currentTime()
        val holds = lockedSeats.map { seat -> seat.reserve(UUID.randomUUID(), now) }
        val group = SeatHoldGroup(
            holdId = UUID.randomUUID(),
            expiresAt = holds.first().expiresAt,
            members = lockedSeats.zip(holds) { seat, hold -> SeatHoldGroupMember(seat.seatId, hold.id) },
        )
        lockedSeats.forEach(seats::save)
        groups.insert(showingId, group)
        return group
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun status(showingId: Long, holdId: UUID): SeatHoldGroup {
        val group = lockGroup(showingId, holdId)
        if (group.bookingId != null) return group

        liveSeats(showingId, group)
        return group
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun release(showingId: Long, holdId: UUID) {
        val group = lockGroup(showingId, holdId)
        if (group.bookingId != null) throw SeatHoldGroupUnavailable()
        if (group.releasedAt != null) return

        val lockedSeats = lockMembers(showingId, group)
        val now = currentTime()
        for ((member, seat) in lockedSeats) {
            if (seat.release(member.holdId)) seats.save(seat)
        }
        groups.markReleased(holdId, now)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun confirm(showingId: Long, holdId: UUID, customer: CustomerDetails): BookingConfirmation {
        val group = lockGroup(showingId, holdId)
        group.bookingId?.let { return BookingConfirmation.Replayed(it) }

        val (lockedSeats, now) = liveSeats(showingId, group)
        for ((member, seat) in lockedSeats) {
            seat.confirm(member.holdId, UUID.randomUUID(), customer, now)
        }
        lockedSeats.forEach { (_, seat) -> seats.save(seat) }
        val bookingId = UUID.randomUUID()
        groups.markConfirmed(holdId, bookingId)
        return BookingConfirmation.Created(bookingId)
    }

    private fun lockGroup(showingId: Long, holdId: UUID): SeatHoldGroup {
        showings.lockRevision(showingId) ?: throw ShowingNotFound(showingId)
        return groups.findForUpdate(showingId, holdId) ?: throw SeatHoldGroupNotFound()
    }

    private fun lockSeat(showingId: Long, seatId: Long): ShowSeat =
        seats.findForUpdate(showingId, seatId) ?: throw SeatNotFound(showingId, seatId)

    private fun lockMembers(showingId: Long, group: SeatHoldGroup): List<Pair<SeatHoldGroupMember, ShowSeat>> =
        group.members.sortedBy { it.seatId }.map { member -> member to lockSeat(showingId, member.seatId) }

    private fun liveSeats(showingId: Long, group: SeatHoldGroup): LiveSeats {
        if (group.releasedAt != null) throw SeatHoldGroupUnavailable()

        val lockedSeats = lockMembers(showingId, group)
        val now = currentTime()
        if (group.expiresAt <= now || lockedSeats.any { (member, seat) -> seat.hold?.id != member.holdId }) {
            throw SeatHoldGroupUnavailable()
        }
        return LiveSeats(lockedSeats, now)
    }

    private fun currentTime(): Instant = clock.instant().truncatedTo(ChronoUnit.MICROS)

    private data class LiveSeats(val seats: List<Pair<SeatHoldGroupMember, ShowSeat>>, val now: Instant)
}

class SeatHoldGroupNotFound : RuntimeException("The seat hold does not exist for this showing.")

class SeatHoldGroupUnavailable : RuntimeException("The seat hold is no longer available for this operation.")
