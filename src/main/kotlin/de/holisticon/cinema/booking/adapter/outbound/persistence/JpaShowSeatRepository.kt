package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.domain.ConfirmedBooking
import de.holisticon.cinema.booking.domain.CustomerDetails
import de.holisticon.cinema.booking.domain.SeatHold
import de.holisticon.cinema.booking.domain.ShowSeat
import de.holisticon.cinema.booking.domain.ShowSeatRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository

@Repository
class JpaShowSeatRepository(private val entityManager: EntityManager) : ShowSeatRepository {
    override fun findForUpdate(showingId: Long, seatId: Long): ShowSeat? {
        val record = entityManager.find(
            ShowSeatJpaEntity::class.java,
            ShowSeatJpaId(showingId, seatId),
            LockModeType.PESSIMISTIC_WRITE,
        ) ?: return null

        val hold = record.holdId?.let { holdId ->
            SeatHold(holdId, checkNotNull(record.holdExpiresAt))
        }
        val booking = entityManager.find(SeatBookingJpaEntity::class.java, record.id)?.toDomain()
        return ShowSeat(showingId, seatId, hold, booking)
    }

    override fun save(seat: ShowSeat) {
        val record = checkNotNull(
            entityManager.find(ShowSeatJpaEntity::class.java, ShowSeatJpaId(seat.showingId, seat.seatId)),
        ) { "Expected a stored seat for showing ${seat.showingId}, seat ${seat.seatId}." }

        record.holdId = seat.hold?.id
        record.holdExpiresAt = seat.hold?.expiresAt
        seat.booking?.let { entityManager.persist(it.toRecord(record.id)) }
    }
}

private fun SeatBookingJpaEntity.toDomain() = ConfirmedBooking(
    bookingId,
    holdId,
    confirmedAt,
    CustomerDetails(customerName, birthDate, email, phone, termsAccepted, privacyAccepted, newsletterOptIn),
)

private fun ConfirmedBooking.toRecord(seat: ShowSeatJpaId) = SeatBookingJpaEntity(
    seat = seat,
    bookingId = id,
    holdId = holdId,
    confirmedAt = confirmedAt,
    customerName = customer.name,
    birthDate = customer.birthDate,
    email = customer.email,
    phone = customer.phone,
    termsAccepted = customer.termsAccepted,
    privacyAccepted = customer.privacyAccepted,
    newsletterOptIn = customer.newsletterOptIn,
)
