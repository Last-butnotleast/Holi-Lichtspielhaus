package de.holisticon.cinema.booking.domain

interface ShowSeatRepository {
    fun findForUpdate(showingId: Long, seatId: Long): ShowSeat?

    fun save(seat: ShowSeat)
}
