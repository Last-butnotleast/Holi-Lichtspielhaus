package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.booking.application.query.SeatMapView
import de.holisticon.cinema.booking.application.query.SeatView
import de.holisticon.cinema.booking.application.query.ShowingView
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

internal const val ADMIN_BEARER_SECURITY = "adminBearer"
internal const val ADMIN_UNAUTHORIZED_RESPONSE = "#/components/responses/AdminUnauthorized"

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun cinemaBookingOpenApi(): OpenAPI = OpenAPI()
        .info(Info().title("Cinema Booking API").version("1.0.0"))
        .servers(listOf(Server().url("/")))
        .components(
            Components()
                .addSecuritySchemes(
                    ADMIN_BEARER_SECURITY,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"),
                )
                .addResponses(
                    "AdminUnauthorized",
                    ApiResponse().description("The admin bearer credential is absent or invalid.")
                        .addHeaderObject(
                            HttpHeaders.WWW_AUTHENTICATE,
                            Header().description("Bearer authentication challenge.").schema(StringSchema()),
                        ),
                ),
        )

    @Bean
    fun requiredResponseFields(): OpenApiCustomizer = OpenApiCustomizer { api ->
        val requiredFields = mapOf(
            BookingResponse::class.java.simpleName to listOf(BookingResponse::bookingId.name),
            SeatHoldResponse::class.java.simpleName to listOf(SeatHoldResponse::holdId.name, SeatHoldResponse::expiresAt.name),
            SeatMapView::class.java.simpleName to listOf(SeatMapView::showingId.name, SeatMapView::seats.name),
            SeatView::class.java.simpleName to listOf(SeatView::seatId.name, SeatView::status.name),
            ShowingView::class.java.simpleName to listOf(
                ShowingView::showingId.name,
                ShowingView::movieTitle.name,
                ShowingView::startsAt.name,
            ),
        )
        val schemas = api.components.schemas
        requiredFields.forEach { (name, fields) -> schemas.getValue(name).required = fields }
    }
}
