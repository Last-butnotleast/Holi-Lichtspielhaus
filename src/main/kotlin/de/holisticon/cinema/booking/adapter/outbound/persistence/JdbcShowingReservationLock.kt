package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.application.port.ShowingReservationLock
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcShowingReservationLock(private val jdbc: JdbcClient) : ShowingReservationLock {
    override fun lockRevision(showingId: Long): Int? =
        jdbc.sql("select revision from showings where id = :showingId for key share")
            .param("showingId", showingId)
            .query(Int::class.javaObjectType)
            .optional()
            .orElse(null)
}
