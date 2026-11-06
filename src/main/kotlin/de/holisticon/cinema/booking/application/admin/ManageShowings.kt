package de.holisticon.cinema.booking.application.admin

import de.holisticon.cinema.booking.application.port.ShowingsWriter
import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.application.query.ShowingView
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ManageShowings(private val showings: ShowingsWriter, private val clock: Clock) {
    @Transactional
    fun create(movieTitle: String, startsAt: Instant): ShowingView {
        val start = startsAt.truncatedTo(ChronoUnit.MICROS)
        return showings.create(movieTitle, start)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun update(showingId: Long, movieTitle: String, startsAt: Instant): ShowingView {
        lockEditableShowing(showingId)
        val start = startsAt.truncatedTo(ChronoUnit.MICROS)
        val revision = showings.update(showingId, movieTitle, start)
        return ShowingView(showingId, movieTitle, start, revision)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun delete(showingId: Long) {
        lockEditableShowing(showingId)
        showings.delete(showingId)
    }

    private fun lockEditableShowing(showingId: Long) {
        if (!showings.lockShowingAndSeats(showingId)) throw ShowingNotFound(showingId)

        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        if (showings.hasAllocations(showingId, now)) throw ShowingHasAllocations(showingId)
    }
}

class ShowingHasAllocations(showingId: Long) :
    RuntimeException("Showing $showingId has active holds or confirmed bookings.")
