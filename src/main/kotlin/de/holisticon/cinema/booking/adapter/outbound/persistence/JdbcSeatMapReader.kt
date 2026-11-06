package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.application.port.SeatAvailabilitySnapshot
import de.holisticon.cinema.booking.application.port.SeatMapReader
import java.time.OffsetDateTime
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcSeatMapReader(private val jdbc: NamedParameterJdbcTemplate) : SeatMapReader {
    override fun findSeats(showingId: Long): List<SeatAvailabilitySnapshot>? =
        jdbc.query(
            """
            select seat.seat_id, seat.hold_expires_at, (booking.booking_id is not null) as booked
            from showings showing
            left join show_seats seat on seat.showing_id = showing.id
            left join seat_bookings booking
                on booking.showing_id = seat.showing_id and booking.seat_id = seat.seat_id
            where showing.id = :showingId
            order by seat.seat_id
            """.trimIndent(),
            mapOf("showingId" to showingId),
            ResultSetExtractor { rows ->
                if (!rows.next()) return@ResultSetExtractor null

                buildList {
                    do {
                        val seatId = rows.getLong("seat_id")
                        if (!rows.wasNull()) {
                            val expiresAt = rows.getObject("hold_expires_at", OffsetDateTime::class.java)?.toInstant()
                            add(SeatAvailabilitySnapshot(seatId, expiresAt, rows.getBoolean("booked")))
                        }
                    } while (rows.next())
                }
            },
        )
}
