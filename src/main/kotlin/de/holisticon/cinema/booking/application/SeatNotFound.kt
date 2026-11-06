package de.holisticon.cinema.booking.application

class SeatNotFound(showingId: Long, seatId: Long) :
    RuntimeException("Seat $seatId does not exist in showing $showingId.")
