package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.application.reserve.SeatNotFound
import de.holisticon.cinema.booking.domain.SeatAlreadyHeld
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [BookingController::class])
class ApiExceptionHandler {
    @ExceptionHandler(SeatAlreadyHeld::class)
    fun seatAlreadyHeld(error: SeatAlreadyHeld): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(SeatNotFound::class, ShowingNotFound::class)
    fun notFound(error: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())
}
