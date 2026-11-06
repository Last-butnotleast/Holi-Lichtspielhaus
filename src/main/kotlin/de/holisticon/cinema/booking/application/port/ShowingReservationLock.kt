package de.holisticon.cinema.booking.application.port

interface ShowingReservationLock {
    fun lockRevision(showingId: Long): Int?
}
