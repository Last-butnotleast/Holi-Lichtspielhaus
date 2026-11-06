package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.TestcontainersConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, SeatHoldHttpTests.ClockConfiguration::class)
class SeatHoldHttpTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var clock: ControlledClock

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbc: JdbcClient

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @Test
    fun `only one contender can hold a seat when the previous hold expires during the lock wait`() {
        clock.set(Instant.parse("2026-11-06T12:00:00Z"))
        val previousHold = createdHold(hold(1))
        clock.set(previousHold.expiresAt.minusNanos(1))

        val requests = dataSource.connection.use { gate ->
            gate.autoCommit = false
            try {
                gate.createStatement().use { statement ->
                    statement.executeQuery(
                        "select 1 from show_seats where showing_id = 1 and seat_id = 1 for update",
                    ).use { rows -> assertTrue(rows.next()) }
                }

                val contenders = List(2) {
                    client.sendAsync(holdRequest(1), HttpResponse.BodyHandlers.ofString())
                }
                awaitContendersWaitingOnSeat()
                clock.set(previousHold.expiresAt)
                contenders
            } finally {
                gate.rollback()
            }
        }

        val responses = requests.map { it.get(10, TimeUnit.SECONDS) }
        assertEquals(listOf(201, 409), responses.map { it.statusCode() }.sorted(), responses.toString())
        val winner = createdHold(responses.single { it.statusCode() == 201 })
        assertNotEquals(previousHold.holdId, winner.holdId)
        assertEquals(previousHold.expiresAt.plusSeconds(300), winner.expiresAt)
        val persistedHoldId = jdbc.sql("select hold_id from show_seats where showing_id = 1 and seat_id = 1")
            .query(UUID::class.java)
            .single()
        assertEquals(winner.holdId, persistedHoldId)
        assertSeatStatus(1, "HELD")
    }

    @Test
    fun `the seat is available exactly at the returned expiry and can be held again`() {
        val start = Instant.parse("2026-11-06T13:00:00.123456789Z")
        clock.set(start)
        val firstHold = createdHold(hold(2))
        assertEquals(start.truncatedTo(ChronoUnit.MICROS).plusSeconds(300), firstHold.expiresAt)

        clock.set(firstHold.expiresAt.minusNanos(1))
        assertSeatStatus(2, "HELD")
        assertEquals(409, hold(2).statusCode())

        clock.set(firstHold.expiresAt)
        assertSeatStatus(2, "AVAILABLE")
        val nextHold = createdHold(hold(2))
        assertNotEquals(firstHold.holdId, nextHold.holdId)
        assertEquals(firstHold.expiresAt.plusSeconds(300), nextHold.expiresAt)
        assertSeatStatus(2, "HELD")
    }

    @Test
    fun `unknown showings and seats return not found`() {
        assertEquals(404, get("/showings/999/seats").statusCode())
        assertEquals(404, hold(1, showingId = 999).statusCode())
        assertEquals(404, hold(201).statusCode())
    }

    private fun awaitContendersWaitingOnSeat() {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        dataSource.connection.use { observer ->
            observer.prepareStatement(
                """
                select count(*) from pg_stat_activity
                where datname = current_database()
                  and wait_event_type = 'Lock'
                  and query like '%show_seats%'
                """.trimIndent(),
            ).use { statement ->
                while (System.nanoTime() < deadline) {
                    statement.executeQuery().use { rows ->
                        rows.next()
                        if (rows.getInt(1) == 2) return
                    }
                    Thread.sleep(20)
                }
            }
        }
        fail("Both hold requests must reach PostgreSQL and wait on the seat lock.")
    }

    private fun hold(seatId: Long, showingId: Long = 1): HttpResponse<String> =
        client.send(holdRequest(seatId, showingId), HttpResponse.BodyHandlers.ofString())

    private fun holdRequest(seatId: Long, showingId: Long = 1): HttpRequest =
        request("/showings/$showingId/seats/$seatId/holds").POST(HttpRequest.BodyPublishers.noBody()).build()

    private fun get(path: String): HttpResponse<String> =
        client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun request(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(20))

    private fun createdHold(response: HttpResponse<String>): CreatedHold {
        assertEquals(201, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        return CreatedHold(
            UUID.fromString(body.required("holdId").stringValue()),
            Instant.parse(body.required("expiresAt").stringValue()),
        )
    }

    private fun assertSeatStatus(seatId: Long, expectedStatus: String) {
        val response = get("/showings/1/seats")
        assertEquals(200, response.statusCode(), response.body())
        val seatMap = json.readTree(response.body())
        assertEquals(1L, seatMap.required("showingId").longValue())
        val seatArray = seatMap.required("seats")
        assertTrue(seatArray.isArray)
        val seats = seatArray.values()
        assertEquals((1L..200L).toList(), seats.map { it.required("seatId").longValue() })
        val seat = seats.single { it.required("seatId").longValue() == seatId }
        assertEquals(expectedStatus, seat.required("status").stringValue())
        assertEquals(2, seat.size(), "The public seat map should contain only seat identity and status.")
    }

    private data class CreatedHold(val holdId: UUID, val expiresAt: Instant)

    @TestConfiguration(proxyBeanMethods = false)
    class ClockConfiguration {
        @Bean
        @Primary
        fun controlledClock(): ControlledClock =
            ControlledClock(AtomicReference(Instant.EPOCH), ZoneOffset.UTC)
    }

    class ControlledClock(private val now: AtomicReference<Instant>, private val timeZone: ZoneId) : Clock() {
        fun set(instant: Instant) {
            now.set(instant)
        }

        override fun instant(): Instant = now.get()

        override fun getZone(): ZoneId = timeZone

        override fun withZone(zone: ZoneId): Clock = ControlledClock(now, zone)
    }
}
