package de.holisticon.cinema.booking.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CustomerDetails(
    val name: String,
    val birthDate: LocalDate,
    val email: String,
    val phone: String?,
    val termsAccepted: Boolean,
    val privacyAccepted: Boolean,
    val newsletterOptIn: Boolean,
) {
    init {
        if (!termsAccepted || !privacyAccepted) {
            throw RequiredConsentsMissing()
        }
    }
}

class ConfirmedBooking(
    val id: UUID,
    val holdId: UUID,
    val confirmedAt: Instant,
    val customer: CustomerDetails,
)

sealed interface BookingConfirmation {
    val bookingId: UUID

    data class Created(override val bookingId: UUID) : BookingConfirmation

    data class Replayed(override val bookingId: UUID) : BookingConfirmation
}

class RequiredConsentsMissing : RuntimeException("Booking requires acceptance of terms and privacy.")
