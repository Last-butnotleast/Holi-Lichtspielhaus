package de.holisticon.cinema.booking.adapter.inbound.web

import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class SeatHoldResponseTest {
    @Test
    fun `the string representation omits the hold credential`() {
        val holdId = UUID.randomUUID()
        val response = SeatHoldResponse(holdId, Instant.parse("2026-11-06T12:05:00Z"))

        assertFalse(holdId.toString() in response.toString())
    }
}
