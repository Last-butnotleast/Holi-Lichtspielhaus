package de.holisticon.cinema

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CinemaBookingApplication

fun main(args: Array<String>) {
	runApplication<CinemaBookingApplication>(*args)
}
