package de.holisticon.cinema.booking.application.port

import de.holisticon.cinema.booking.application.query.ShowingView
import java.time.Instant

interface ShowingsWriter {
    fun create(movieTitle: String, startsAt: Instant): ShowingView

    fun lockShowingAndSeats(showingId: Long): Boolean

    fun hasAllocations(showingId: Long, now: Instant): Boolean

    fun update(showingId: Long, movieTitle: String, startsAt: Instant): Int

    fun delete(showingId: Long)
}
