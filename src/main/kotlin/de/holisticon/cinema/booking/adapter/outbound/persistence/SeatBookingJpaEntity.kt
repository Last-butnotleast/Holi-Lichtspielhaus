package de.holisticon.cinema.booking.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.hibernate.annotations.Immutable

@Entity
@Immutable
@Table(name = "seat_bookings")
class SeatBookingJpaEntity(
    @EmbeddedId val seat: ShowSeatJpaId,
    @Column(name = "booking_id") val bookingId: UUID,
    @Column(name = "hold_id") val holdId: UUID,
    @Column(name = "confirmed_at") val confirmedAt: Instant,
    @Column(name = "customer_name") val customerName: String,
    @Column(name = "birth_date") val birthDate: LocalDate,
    @Column(name = "email") val email: String,
    @Column(name = "phone") val phone: String?,
    @Column(name = "terms_accepted") val termsAccepted: Boolean,
    @Column(name = "privacy_accepted") val privacyAccepted: Boolean,
    @Column(name = "newsletter_opt_in") val newsletterOptIn: Boolean,
)
