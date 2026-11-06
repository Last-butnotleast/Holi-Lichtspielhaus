package de.holisticon.cinema.booking.application.reserve

import de.holisticon.cinema.booking.application.port.ShowingReservationLock
import de.holisticon.cinema.booking.application.query.ShowingNotFound

internal fun ShowingReservationLock.requireRevision(showingId: Long, expectedRevision: Int) {
    val revision = lockRevision(showingId) ?: throw ShowingNotFound(showingId)
    if (revision != expectedRevision) throw ShowingChanged(showingId)
}

class ShowingChanged(showingId: Long) :
    RuntimeException("The film or start time of showing $showingId changed. Review the updated showing before reserving seats.")
