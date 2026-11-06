package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.application.port.ShowingsWriter
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

private const val SEATS_PER_SHOWING = 200

@Repository
class JdbcShowingsWriter(private val jdbc: JdbcClient) : ShowingsWriter {
    override fun create(movieTitle: String, startsAt: Instant): Long {
        val showingId = jdbc.sql(
            "insert into showings (movie_title, starts_at) values (:title, :startsAt) returning id",
        )
            .param("title", movieTitle)
            .param("startsAt", startsAt.atOffset(ZoneOffset.UTC))
            .query(Long::class.javaObjectType)
            .single()

        jdbc.sql(
            """
            insert into show_seats (showing_id, seat_id)
            select :showingId, seat_id from generate_series(1, :seatCount) as seats(seat_id)
            """.trimIndent(),
        )
            .param("showingId", showingId)
            .param("seatCount", SEATS_PER_SHOWING)
            .update()
        return showingId
    }

    override fun lockShowingAndSeats(showingId: Long): Boolean {
        val showing = jdbc.sql("select id from showings where id = :showingId for update")
            .param("showingId", showingId)
            .query(Long::class.javaObjectType)
            .optional()
        if (showing.isEmpty) return false

        jdbc.sql("select seat_id from show_seats where showing_id = :showingId order by seat_id for update")
            .param("showingId", showingId)
            .query(Long::class.javaObjectType)
            .list()
        return true
    }

    override fun hasAllocations(showingId: Long, now: Instant): Boolean =
        jdbc.sql(
            """
            select exists (
                select 1 from show_seats where showing_id = :showingId and hold_expires_at > :now
            ) or exists (
                select 1 from seat_bookings where showing_id = :showingId
            )
            """.trimIndent(),
        )
            .param("showingId", showingId)
            .param("now", now.atOffset(ZoneOffset.UTC))
            .query(Boolean::class.javaObjectType)
            .single()

    override fun update(showingId: Long, movieTitle: String, startsAt: Instant) {
        jdbc.sql("update showings set movie_title = :title, starts_at = :startsAt where id = :showingId")
            .param("title", movieTitle)
            .param("startsAt", startsAt.atOffset(ZoneOffset.UTC))
            .param("showingId", showingId)
            .update()
    }

    override fun delete(showingId: Long) {
        jdbc.sql("delete from show_seats where showing_id = :showingId").param("showingId", showingId).update()
        jdbc.sql("delete from showings where id = :showingId").param("showingId", showingId).update()
    }
}
