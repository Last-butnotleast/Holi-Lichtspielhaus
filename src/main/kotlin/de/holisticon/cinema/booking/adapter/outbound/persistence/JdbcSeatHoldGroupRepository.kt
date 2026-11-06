package de.holisticon.cinema.booking.adapter.outbound.persistence

import de.holisticon.cinema.booking.application.port.SeatHoldGroup
import de.holisticon.cinema.booking.application.port.SeatHoldGroupMember
import de.holisticon.cinema.booking.application.port.SeatHoldGroupRepository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcSeatHoldGroupRepository(private val jdbc: JdbcClient) : SeatHoldGroupRepository {
    override fun insert(showingId: Long, group: SeatHoldGroup) {
        jdbc.sql(
            "insert into seat_hold_groups (hold_id, showing_id, expires_at) values (:holdId, :showingId, :expiresAt)",
        )
            .param("holdId", group.holdId)
            .param("showingId", showingId)
            .param("expiresAt", group.expiresAt.atOffset(ZoneOffset.UTC))
            .update()

        for (member in group.members) {
            jdbc.sql(
                """
                insert into seat_hold_group_members (group_hold_id, seat_id, member_hold_id)
                values (:groupHoldId, :seatId, :memberHoldId)
                """.trimIndent(),
            )
                .param("groupHoldId", group.holdId)
                .param("seatId", member.seatId)
                .param("memberHoldId", member.holdId)
                .update()
        }
    }

    override fun findForUpdate(showingId: Long, holdId: UUID): SeatHoldGroup? {
        val header = jdbc.sql(
            """
            select expires_at, released_at, booking_id
            from seat_hold_groups where showing_id = :showingId and hold_id = :holdId
            for update
            """.trimIndent(),
        )
            .param("showingId", showingId)
            .param("holdId", holdId)
            .query { row, _ ->
                GroupHeader(
                    expiresAt = row.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
                    releasedAt = row.getObject("released_at", OffsetDateTime::class.java)?.toInstant(),
                    bookingId = row.getObject("booking_id", UUID::class.java),
                )
            }
            .optional()
            .orElse(null) ?: return null

        val members = jdbc.sql(
            """
            select seat_id, member_hold_id from seat_hold_group_members
            where group_hold_id = :holdId order by seat_id
            """.trimIndent(),
        )
            .param("holdId", holdId)
            .query { row, _ -> SeatHoldGroupMember(row.getLong("seat_id"), row.getObject("member_hold_id", UUID::class.java)) }
            .list()
        return SeatHoldGroup(holdId, header.expiresAt, members, header.releasedAt, header.bookingId)
    }

    override fun markReleased(holdId: UUID, releasedAt: Instant) {
        jdbc.sql("update seat_hold_groups set released_at = :releasedAt where hold_id = :holdId")
            .param("releasedAt", releasedAt.atOffset(ZoneOffset.UTC))
            .param("holdId", holdId)
            .update()
    }

    override fun markConfirmed(holdId: UUID, bookingId: UUID) {
        jdbc.sql("update seat_hold_groups set booking_id = :bookingId where hold_id = :holdId")
            .param("bookingId", bookingId)
            .param("holdId", holdId)
            .update()
    }

    override fun isMemberHold(holdId: UUID): Boolean =
        jdbc.sql("select exists (select 1 from seat_hold_group_members where member_hold_id = :holdId)")
            .param("holdId", holdId)
            .query(Boolean::class.javaObjectType)
            .single()

    private class GroupHeader(val expiresAt: Instant, val releasedAt: Instant?, val bookingId: UUID?)
}
