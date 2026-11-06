package de.holisticon.cinema.booking.configuration

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class BookingConfiguration {
    @Bean
    fun bookingClock(): Clock = Clock.systemUTC()
}
