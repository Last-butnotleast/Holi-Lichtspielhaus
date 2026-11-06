package de.holisticon.cinema.booking.application.query

data class SeatMapView(val showingId: Long, val seats: List<SeatView>)

data class SeatView(val seatId: Long, val status: SeatStatus)

enum class SeatStatus {
    AVAILABLE,
    HELD,
}
