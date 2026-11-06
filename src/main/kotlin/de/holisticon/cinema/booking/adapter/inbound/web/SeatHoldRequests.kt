package de.holisticon.cinema.booking.adapter.inbound.web

import com.fasterxml.jackson.annotation.JsonCreator
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import tools.jackson.databind.JsonNode

@Schema(requiredProperties = ["expectedShowingRevision"])
class ReserveSeatRequest private constructor(
    @get:Schema(minimum = "1")
    val expectedShowingRevision: Int,
) {
    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(body: JsonNode): ReserveSeatRequest =
            ReserveSeatRequest(body.requiredShowingRevision())
    }
}

@Schema(requiredProperties = ["seatIds", "expectedShowingRevision"])
class ReserveSeatGroupRequest private constructor(
    @get:ArraySchema(minItems = 1, uniqueItems = true, schema = Schema(type = "integer", format = "int64", minimum = "1"))
    val seatIds: Set<Long>,
    @get:Schema(minimum = "1")
    val expectedShowingRevision: Int,
) {
    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(body: JsonNode): ReserveSeatGroupRequest {
            if (!body.isObject) throw InvalidBookingRequest()

            val entries = body.get("seatIds")?.takeIf { it.isArray && !it.isEmpty } ?: throw InvalidBookingRequest()
            val seatIds = entries.values().map { entry ->
                if (!entry.isIntegralNumber || !entry.canConvertToLong()) throw InvalidBookingRequest()
                entry.longValue().takeIf { it > 0 } ?: throw InvalidBookingRequest()
            }.toSet()
            if (seatIds.size != entries.size()) throw InvalidBookingRequest()

            return ReserveSeatGroupRequest(seatIds, body.requiredShowingRevision())
        }
    }
}

private fun JsonNode.requiredShowingRevision(): Int {
    val revision = get("expectedShowingRevision")
        ?.takeIf { it.isIntegralNumber && it.canConvertToInt() }
        ?.intValue()
        ?: throw InvalidBookingRequest()
    return revision.takeIf { it > 0 } ?: throw InvalidBookingRequest()
}

@Schema(requiredProperties = ["holdId"])
class SeatHoldCredentialRequest private constructor(val holdId: UUID) {
    override fun toString(): String = "SeatHoldCredentialRequest"

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(body: JsonNode): SeatHoldCredentialRequest {
            if (!body.isObject) throw InvalidBookingRequest()
            return SeatHoldCredentialRequest(body.requiredHoldId())
        }
    }
}
