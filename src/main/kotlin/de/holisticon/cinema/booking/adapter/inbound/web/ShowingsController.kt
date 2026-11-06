package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.query.ListShowings
import de.holisticon.cinema.booking.application.query.ShowingView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ShowingsController(private val listShowings: ListShowings) {
    @GetMapping("/showings")
    fun showings(): List<ShowingView> = listShowings.list()
}
