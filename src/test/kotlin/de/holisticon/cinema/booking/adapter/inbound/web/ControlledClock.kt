package de.holisticon.cinema.booking.adapter.inbound.web

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class ControlledClockConfiguration {
    @Bean
    @Primary
    fun controlledClock(): ControlledClock =
        ControlledClock(AtomicReference(Instant.EPOCH), ZoneOffset.UTC)
}

class ControlledClock(private val now: AtomicReference<Instant>, private val timeZone: ZoneId) : Clock() {
    fun set(instant: Instant) {
        now.set(instant)
    }

    override fun instant(): Instant = now.get()

    override fun getZone(): ZoneId = timeZone

    override fun withZone(zone: ZoneId): Clock = ControlledClock(now, zone)
}
