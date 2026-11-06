package de.holisticon.cinema.booking.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ShowSeatJpaId(
    @Column(name = "showing_id") val showingId: Long,
    @Column(name = "seat_id") val seatId: Long,
) : Serializable

@Entity
@Table(name = "show_seats")
class ShowSeatJpaEntity(
    @EmbeddedId val id: ShowSeatJpaId,
    @Column(name = "hold_id") var holdId: UUID?,
    @Column(name = "hold_expires_at") var holdExpiresAt: Instant?,
)
