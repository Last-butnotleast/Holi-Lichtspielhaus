package de.holisticon.cinema

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<CinemaBookingApplication>().with(TestcontainersConfiguration::class).run(*args)
}
