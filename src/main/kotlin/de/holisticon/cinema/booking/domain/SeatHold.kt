package de.holisticon.cinema.booking.domain

import java.time.Instant
import java.util.UUID

data class SeatHold(val id: UUID, val expiresAt: Instant)

internal fun isHoldActive(expiresAt: Instant?, now: Instant): Boolean =
    expiresAt?.isAfter(now) == true
