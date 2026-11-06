package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.application.port.ShowingsReader
import de.holisticon.cinema.booking.application.query.ShowingView
import java.time.OffsetDateTime
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcShowingsReader(private val jdbc: JdbcClient) : ShowingsReader {
    override fun list(): List<ShowingView> =
        jdbc.sql("select id, movie_title, starts_at from showings order by starts_at, id")
            .query { row, _ ->
                ShowingView(
                    showingId = row.getLong("id"),
                    movieTitle = row.getString("movie_title"),
                    startsAt = row.getObject("starts_at", OffsetDateTime::class.java).toInstant(),
                )
            }
            .list()
}
