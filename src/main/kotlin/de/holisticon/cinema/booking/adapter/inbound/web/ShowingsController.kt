package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.query.ListShowings
import de.holisticon.cinema.booking.application.query.ShowingView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ShowingsController(private val listShowings: ListShowings) {
    @Operation(
        operationId = "listShowings",
        summary = "List showings in start-time order",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Showings ordered by start time, then showing ID.",
                content = [Content(
                    mediaType = APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = ShowingView::class)),
                )],
            ),
        ],
    )
    @GetMapping("/showings")
    fun showings(): List<ShowingView> = listShowings.list()
}
