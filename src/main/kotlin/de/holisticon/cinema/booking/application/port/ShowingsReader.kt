package de.holisticon.cinema.booking.application.port

import de.holisticon.cinema.booking.application.query.ShowingView

interface ShowingsReader {
    fun list(): List<ShowingView>
}
