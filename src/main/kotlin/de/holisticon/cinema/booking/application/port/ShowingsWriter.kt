package de.holisticon.cinema.booking.application.port

import java.time.Instant

interface ShowingsWriter {
    fun create(movieTitle: String, startsAt: Instant): Long

    fun lockShowingAndSeats(showingId: Long): Boolean

    fun hasAllocations(showingId: Long, now: Instant): Boolean

    fun update(showingId: Long, movieTitle: String, startsAt: Instant)

    fun delete(showingId: Long)
}
