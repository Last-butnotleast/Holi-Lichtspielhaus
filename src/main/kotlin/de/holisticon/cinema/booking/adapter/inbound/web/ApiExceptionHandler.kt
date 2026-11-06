package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.SeatNotFound
import de.holisticon.cinema.booking.application.group.SeatHoldGroupNotFound
import de.holisticon.cinema.booking.application.group.SeatHoldGroupUnavailable
import de.holisticon.cinema.booking.application.query.SeatMapTemporarilyUnavailable
import de.holisticon.cinema.booking.application.query.ShowingNotFound
import de.holisticon.cinema.booking.domain.BirthDateInFuture
import de.holisticon.cinema.booking.domain.HoldNotConfirmable
import de.holisticon.cinema.booking.domain.RequiredConsentsMissing
import de.holisticon.cinema.booking.domain.SeatAlreadyBooked
import de.holisticon.cinema.booking.domain.SeatAlreadyHeld
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [BookingController::class, SeatHoldGroupController::class])
class ApiExceptionHandler {
    @ExceptionHandler(
        SeatAlreadyHeld::class,
        SeatAlreadyBooked::class,
        HoldNotConfirmable::class,
        SeatHoldGroupUnavailable::class,
    )
    fun conflict(error: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(SeatNotFound::class, ShowingNotFound::class, SeatHoldGroupNotFound::class)
    fun notFound(error: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(SeatMapTemporarilyUnavailable::class)
    fun temporarilyUnavailable(error: SeatMapTemporarilyUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, error.message.orEmpty())

    @ExceptionHandler(BirthDateInFuture::class)
    fun futureBirthDate(error: BirthDateInFuture): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.message.orEmpty())

    @ExceptionHandler(
        InvalidBookingRequest::class,
        RequiredConsentsMissing::class,
        HttpMessageNotReadableException::class,
    )
    fun badRequest(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid booking request.")
}
