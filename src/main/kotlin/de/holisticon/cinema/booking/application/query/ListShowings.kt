package de.holisticon.cinema.booking.application.query

import de.holisticon.cinema.booking.application.port.ShowingsReader
import org.springframework.stereotype.Service

@Service
class ListShowings(private val reader: ShowingsReader) {
    fun list(): List<ShowingView> = reader.list()
}
