package de.holisticon.cinema.booking.application.query

import java.time.Instant

data class ShowingView(val showingId: Long, val movieTitle: String, val startsAt: Instant)
