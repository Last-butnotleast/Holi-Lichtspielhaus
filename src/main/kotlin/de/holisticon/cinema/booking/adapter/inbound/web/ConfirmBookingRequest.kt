package de.holisticon.cinema.booking.adapter.inbound.web

import com.fasterxml.jackson.annotation.JsonCreator
import de.holisticon.cinema.booking.domain.CustomerDetails
import jakarta.validation.Validator
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import tools.jackson.databind.JsonNode

class ConfirmBookingRequest private constructor(
    val holdId: UUID,
    @field:NotBlank @field:Size(max = 200) private val name: String,
    private val birthDate: LocalDate,
    @field:NotBlank @field:Email @field:Size(max = 254) private val email: String,
    @field:Size(max = 50) private val phone: String?,
    private val termsAccepted: Boolean,
    private val privacyAccepted: Boolean,
    private val newsletterOptIn: Boolean,
) {
    fun validatedCustomerDetails(validator: Validator): CustomerDetails {
        if (validator.validate(this).isNotEmpty()) throw InvalidBookingRequest()
        return CustomerDetails(name, birthDate, email, phone, termsAccepted, privacyAccepted, newsletterOptIn)
    }

    override fun toString(): String = "ConfirmBookingRequest"

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(body: JsonNode): ConfirmBookingRequest {
            if (!body.isObject) throw InvalidBookingRequest()

            return ConfirmBookingRequest(
                holdId = parseHoldId(body.requiredText("holdId")),
                name = body.requiredText("name"),
                birthDate = parseBirthDate(body.requiredText("birthDate")),
                email = body.requiredText("email"),
                phone = body.textOrNull("phone"),
                termsAccepted = body.booleanOrNull("termsAccepted") ?: throw InvalidBookingRequest(),
                privacyAccepted = body.booleanOrNull("privacyAccepted") ?: throw InvalidBookingRequest(),
                newsletterOptIn = body.booleanOrNull("newsletterOptIn") ?: false,
            )
        }

        private fun parseHoldId(value: String): UUID {
            val id = try {
                UUID.fromString(value)
            } catch (_: IllegalArgumentException) {
                throw InvalidBookingRequest()
            }
            if (!id.toString().equals(value, ignoreCase = true)) throw InvalidBookingRequest()
            return id
        }

        private fun parseBirthDate(value: String): LocalDate {
            if (value.length != 10) throw InvalidBookingRequest()
            return try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                throw InvalidBookingRequest()
            }
        }
    }
}

private fun JsonNode.requiredText(field: String): String = textOrNull(field) ?: throw InvalidBookingRequest()

private fun JsonNode.textOrNull(field: String): String? {
    val value = get(field)?.takeUnless { it.isNull } ?: return null
    if (!value.isString) throw InvalidBookingRequest()
    return value.stringValue().also {
        if ('\u0000' in it) throw InvalidBookingRequest()
    }
}

private fun JsonNode.booleanOrNull(field: String): Boolean? {
    val value = get(field)?.takeUnless { it.isNull } ?: return null
    if (!value.isBoolean) throw InvalidBookingRequest()
    return value.booleanValue()
}

class InvalidBookingRequest : RuntimeException()
