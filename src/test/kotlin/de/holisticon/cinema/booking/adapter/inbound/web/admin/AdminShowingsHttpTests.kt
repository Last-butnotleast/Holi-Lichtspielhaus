package de.holisticon.cinema.booking.adapter.inbound.web.admin

import de.holisticon.cinema.TestcontainersConfiguration
import de.holisticon.cinema.booking.adapter.inbound.web.BookingHttpTests
import de.holisticon.cinema.booking.application.confirm.ConfirmBooking
import de.holisticon.cinema.booking.domain.BookingConfirmation
import de.holisticon.cinema.booking.domain.CustomerDetails
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private const val ADMIN_TOKEN = "admin-test-token"
private const val ADMIN_SHOWINGS = "/admin/showings"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["cinema.admin.token=$ADMIN_TOKEN"],
)
@Import(TestcontainersConfiguration::class, BookingHttpTests.ClockConfiguration::class)
class AdminShowingsHttpTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var clock: BookingHttpTests.ControlledClock

    @Autowired
    private lateinit var confirmBooking: ConfirmBooking

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var dataSource: DataSource

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @Test
    fun `admin writes require a valid bearer token before parsing the request`() {
        val before = programme()
        val missing = send(adminRequest("POST", ADMIN_SHOWINGS, "{", token = null))
        assertEquals(401, missing.statusCode())
        assertEquals("Bearer", missing.headers().firstValue("WWW-Authenticate").orElseThrow())
        assertEquals(
            401,
            send(adminRequest("PUT", "$ADMIN_SHOWINGS/1", showingBody("Forbidden"), token = "wrong")).statusCode(),
        )
        assertEquals(401, send(adminRequest("DELETE", "$ADMIN_SHOWINGS/1", token = null)).statusCode())
        assertEquals(before, programme())
    }

    @Test
    fun `admin can create update and delete an unallocated showing`() {
        val seededIds = programme().map { it.required("showingId").longValue() }.toSet()
        val created = createShowing("Admin matinee", "2027-02-01T15:00:00.123456789+01:00")
        val showingId = created.required("showingId").longValue()
        assertTrue(showingId > seededIds.max())
        assertEquals("Admin matinee", created.required("movieTitle").stringValue())
        assertEquals("2027-02-01T14:00:00.123456Z", created.required("startsAt").stringValue())
        assertEquals(1, created.required("revision").intValue())
        assertEquals(created, showingInProgramme(showingId))
        assertTrue(programme().map { it.required("showingId").longValue() }.containsAll(seededIds))

        val seats = seatMap(showingId)
        assertEquals((1L..200L).toList(), seats.map { it.required("seatId").longValue() })
        assertTrue(seats.all { it.required("status").stringValue() == "AVAILABLE" })

        val updated = send(adminRequest(
            "PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("Admin evening", "2027-02-01T19:00:00+01:00"),
        ))
        assertEquals(200, updated.statusCode(), updated.body())
        val updatedShowing = json.readTree(updated.body())
        assertEquals(showingId, updatedShowing.required("showingId").longValue())
        assertEquals("Admin evening", updatedShowing.required("movieTitle").stringValue())
        assertEquals("2027-02-01T18:00:00Z", updatedShowing.required("startsAt").stringValue())
        assertEquals(2, updatedShowing.required("revision").intValue())
        assertEquals(updatedShowing, showingInProgramme(showingId))
        assertEquals(
            updatedShowing,
            readJson(send(adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("Admin evening", "2027-02-01T19:00:00+01:00"))), 200),
        )
        assertEquals(seats, seatMap(showingId))

        val deleted = send(adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId"))
        assertEquals(204, deleted.statusCode())
        assertEquals("", deleted.body())
        assertFalse(programme().any { it.required("showingId").longValue() == showingId })
        assertEquals(404, send(publicRequest("GET", "/showings/$showingId/seats")).statusCode())
        assertEquals(
            404,
            send(adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("Gone"))).statusCode(),
        )
        assertEquals(404, send(adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId")).statusCode())
    }

    @Test
    fun `malformed showing requests return 400`() {
        val invalidBodies = listOf(
            "{",
            "{}",
            showingBody("   "),
            showingBody("No offset", "2027-02-01T15:00:00"),
            showingBody("Invalid date", "2027-02-30T15:00:00Z"),
            """{"movieTitle":12,"startsAt":"2027-02-01T15:00:00Z"}""",
            """{"movieTitle":"Numeric time","startsAt":1800000000}""",
        )

        for (body in invalidBodies) {
            val response = send(adminRequest("POST", ADMIN_SHOWINGS, body))
            assertEquals(400, response.statusCode(), response.body())
        }
        val update = send(adminRequest("PUT", "$ADMIN_SHOWINGS/1", showingBody("No offset", "2027-02-01T15:00:00")))
        assertEquals(400, update.statusCode(), update.body())
    }

    @Test
    fun `active holds block admin writes until they expire during the lock wait`() {
        clock.set(Instant.parse("2026-11-06T10:00:00.123456789Z"))
        val showingId = createShowing("Hold guard").required("showingId").longValue()
        val hold = holdSeat(showingId)
        val expiresAt = Instant.parse(hold.required("expiresAt").stringValue())
        val update = adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("After expiry"))

        clock.set(expiresAt.minusNanos(1))
        assertEquals(409, send(update).statusCode())
        assertEquals(409, send(adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId")).statusCode())

        val pending = checkNotNull(TransactionTemplate(transactionManager).execute {
            jdbc.sql("select seat_id from show_seats where showing_id = :showingId and seat_id = 1 for update")
                .param("showingId", showingId).query(Long::class.javaObjectType).single()
            val response = client.sendAsync(update, HttpResponse.BodyHandlers.ofString())
            awaitAdminSeatLock()
            clock.set(expiresAt)
            response
        })
        assertEquals(200, pending.get(10, TimeUnit.SECONDS).statusCode())
        assertEquals(204, send(adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId")).statusCode())
    }

    @Test
    fun `an admin update sees a booking committed while waiting for its seat lock`() {
        clock.set(Instant.parse("2026-11-06T11:00:00Z"))
        val original = createShowing("Booking guard")
        val showingId = original.required("showingId").longValue()
        val hold = holdSeat(showingId)
        val holdId = UUID.fromString(hold.required("holdId").stringValue())
        val expiresAt = Instant.parse(hold.required("expiresAt").stringValue())

        val pending = checkNotNull(TransactionTemplate(transactionManager).execute {
            assertIs<BookingConfirmation.Created>(
                confirmBooking.confirm(showingId, 1, holdId, customerDetails()),
            )
            clock.set(expiresAt)
            val update = adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("Must stay unchanged"))
            val response = client.sendAsync(update, HttpResponse.BodyHandlers.ofString())
            awaitAdminSeatLock()
            response
        })

        assertEquals(409, pending.get(10, TimeUnit.SECONDS).statusCode())
        assertEquals(409, send(adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId")).statusCode())
        assertEquals(original, showingInProgramme(showingId))
        val bookedSeat = seatMap(showingId).single { it.required("seatId").longValue() == 1L }
        assertEquals("BOOKED", bookedSeat.required("status").stringValue())
    }

    @Test
    fun `an admin edit winning the showing lock rejects both stale holds before allocating seats`() {
        val showingId = createShowing("Before edit").required("showingId").longValue()
        val singlePath = "/showings/$showingId/seats/1/holds"
        val groupPath = "/showings/$showingId/holds"
        val single = mapOf("expectedShowingRevision" to 1)
        val group = single + ("seatIds" to listOf(2, 3))
        val pending = checkNotNull(TransactionTemplate(transactionManager).execute {
            lockFirstSeat(showingId)
            val update = client.sendAsync(
                adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("After edit")),
                HttpResponse.BodyHandlers.ofString(),
            )
            awaitAdminSeatLock()
            val holds = listOf(holdRequest(singlePath, single), holdRequest(groupPath, group)).map {
                client.sendAsync(it, HttpResponse.BodyHandlers.ofString())
            }
            awaitLock("%select revision from showings%for key share%", expected = 2)
            update to holds
        })

        val edited = readJson(pending.first.get(10, TimeUnit.SECONDS), 200)
        val revision = edited.required("revision").intValue()
        assertEquals(2, revision)
        assertEquals(edited, showingInProgramme(showingId))
        for (hold in pending.second) {
            val conflict = readJson(hold.get(10, TimeUnit.SECONDS), 409)
            assertEquals("urn:cinema:problem:showing-changed", conflict.required("type").stringValue())
        }
        assertTrue(seatMap(showingId).all { it.required("status").stringValue() == "AVAILABLE" })
        assertEquals(201, send(holdRequest(singlePath, single + ("expectedShowingRevision" to revision))).statusCode())
        assertEquals(201, send(holdRequest(groupPath, group + ("expectedShowingRevision" to revision))).statusCode())
    }

    @Test
    fun `either hold winning the showing lock blocks a waiting admin edit`() {
        for (group in listOf(false, true)) {
            val original = createShowing("Hold wins")
            val showingId = original.required("showingId").longValue()
            val path = if (group) "/showings/$showingId/holds" else "/showings/$showingId/seats/1/holds"
            val revision = mapOf("expectedShowingRevision" to original.required("revision").intValue())
            val payload = if (group) revision + ("seatIds" to listOf(1, 2)) else revision
            val pending = checkNotNull(TransactionTemplate(transactionManager).execute {
                lockFirstSeat(showingId)
                val hold = client.sendAsync(holdRequest(path, payload), HttpResponse.BodyHandlers.ofString())
                awaitLock("%show_seats%for %")
                val update = client.sendAsync(
                    adminRequest("PUT", "$ADMIN_SHOWINGS/$showingId", showingBody("Must stay unchanged")),
                    HttpResponse.BodyHandlers.ofString(),
                )
                awaitLock("%select id from showings%for update%")
                hold to update
            })

            readJson(pending.first.get(10, TimeUnit.SECONDS), 201)
            assertEquals(409, pending.second.get(10, TimeUnit.SECONDS).statusCode())
            assertEquals(original, showingInProgramme(showingId))
        }
    }

    @Test
    fun `a showing with expired and released groups can be deleted`() {
        clock.set(Instant.parse("2026-11-06T12:00:00Z"))
        val showingId = createShowing("Group cleanup").required("showingId").longValue()
        val expired = holdGroup(showingId, listOf(1, 2))
        val released = holdGroup(showingId, listOf(3, 4))
        val delete = adminRequest("DELETE", "$ADMIN_SHOWINGS/$showingId")
        assertEquals(409, send(delete).statusCode())

        val releaseBody = json.writeValueAsString(mapOf("holdId" to released.required("holdId").stringValue()))
        assertEquals(204, send(adminRequest("POST", "/showings/$showingId/holds/release", releaseBody, token = null)).statusCode())
        clock.set(Instant.parse(expired.required("expiresAt").stringValue()))
        assertEquals(204, send(delete).statusCode())
        assertEquals(
            0L,
            jdbc.sql("select count(*) from seat_hold_groups where showing_id = :showingId")
                .param("showingId", showingId).query(Long::class.javaObjectType).single(),
        )
    }

    private fun holdGroup(showingId: Long, seatIds: List<Int>): JsonNode =
        readJson(
            send(adminRequest("POST", "/showings/$showingId/holds", json.writeValueAsString(mapOf("seatIds" to seatIds, "expectedShowingRevision" to 1)), token = null)),
            201,
        )

    private fun lockFirstSeat(showingId: Long) {
        jdbc.sql("select seat_id from show_seats where showing_id = :showingId and seat_id = 1 for update")
            .param("showingId", showingId).query(Long::class.javaObjectType).single()
    }

    private fun awaitAdminSeatLock() = awaitLock("%order by seat_id for update%")

    private fun awaitLock(queryPattern: String, expected: Int = 1) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        dataSource.connection.use { observer ->
            observer.prepareStatement(
                """
                select count(*) from pg_stat_activity
                where datname = current_database()
                  and wait_event_type = 'Lock'
                  and query like ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, queryPattern)
                while (System.nanoTime() < deadline) {
                    statement.executeQuery().use { rows ->
                        rows.next()
                        if (rows.getInt(1) == expected) return
                    }
                    Thread.sleep(20)
                }
            }
        }
        fail("Expected $expected PostgreSQL lock waiters matching $queryPattern.")
    }

    private fun createShowing(title: String, startsAt: String = "2027-02-01T15:00:00Z"): JsonNode =
        readJson(send(adminRequest("POST", ADMIN_SHOWINGS, showingBody(title, startsAt))), 201)

    private fun showingBody(title: String, startsAt: String = "2027-02-01T15:00:00Z"): String =
        json.writeValueAsString(mapOf("movieTitle" to title, "startsAt" to startsAt))

    private fun programme(): List<JsonNode> =
        readJson(send(publicRequest("GET", "/showings")), 200).values().toList()

    private fun showingInProgramme(showingId: Long): JsonNode =
        programme().single { it.required("showingId").longValue() == showingId }

    private fun seatMap(showingId: Long): List<JsonNode> =
        readJson(send(publicRequest("GET", "/showings/$showingId/seats")), 200).required("seats").values().toList()

    private fun holdSeat(showingId: Long): JsonNode =
        readJson(send(holdRequest("/showings/$showingId/seats/1/holds", mapOf("expectedShowingRevision" to 1))), 201)

    private fun holdRequest(path: String, body: Map<String, Any>): HttpRequest =
        adminRequest("POST", path, json.writeValueAsString(body), token = null)

    private fun adminRequest(
        method: String,
        path: String,
        body: String? = null,
        token: String? = ADMIN_TOKEN,
    ): HttpRequest {
        val request = requestBuilder(path)
        if (token != null) request.header("Authorization", "Bearer $token")
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody()
        else HttpRequest.BodyPublishers.ofString(body).also { request.header("Content-Type", "application/json") }
        return request.method(method, publisher).build()
    }

    private fun publicRequest(method: String, path: String): HttpRequest =
        requestBuilder(path).method(method, HttpRequest.BodyPublishers.noBody()).build()

    private fun requestBuilder(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(20))

    private fun send(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun readJson(response: HttpResponse<String>, expectedStatus: Int): JsonNode {
        assertEquals(expectedStatus, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun customerDetails() = CustomerDetails(
        name = "Admin test customer",
        birthDate = LocalDate.parse("1990-01-01"),
        email = "customer@example.test",
        phone = null,
        termsAccepted = true,
        privacyAccepted = true,
        newsletterOptIn = false,
    )
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["cinema.admin.token="],
)
@Import(TestcontainersConfiguration::class)
class AdminWithoutTokenHttpTests {
    @LocalServerPort
    private var port = 0

    @Test
    fun `a blank token configuration denies admin writes`() {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/admin/showings/1"))
            .header("Authorization", "Bearer $ADMIN_TOKEN")
            .DELETE()
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(401, response.statusCode())
    }
}
