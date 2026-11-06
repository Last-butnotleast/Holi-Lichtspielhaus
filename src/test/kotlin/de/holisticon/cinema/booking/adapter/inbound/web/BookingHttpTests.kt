package de.holisticon.cinema.booking.adapter.inbound.web

import de.holisticon.cinema.TestcontainersConfiguration
import de.holisticon.cinema.booking.application.port.SeatAvailabilitySnapshot
import de.holisticon.cinema.booking.application.port.SeatMapReader
import de.holisticon.cinema.booking.application.query.ShowingView
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(
    TestcontainersConfiguration::class,
    ControlledClockConfiguration::class,
    BookingHttpTests.SeatMapProbeConfiguration::class,
)
class BookingHttpTests {
    @LocalServerPort
    private var port = 0

    @Autowired
    private lateinit var clock: ControlledClock

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var seatMapProbe: SeatMapProbe

    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    @Test
    fun `the generated API contract describes booking and admin operations`() {
        val response = get("/v3/api-docs")
        assertEquals(200, response.statusCode(), response.body())
        val spec = json.readTree(response.body())
        assertEquals("/", spec.at("/servers/0/url").stringValue())
        val operations = spec.required("paths").properties().flatMap { (path, methods) ->
            methods.properties().map { (method, operation) -> "$method $path" to operation }
        }.toMap()
        assertEquals(
            mapOf(
                "get /showings" to "listShowings",
                "get /showings/{showingId}/seats" to "getSeatMap",
                "post /showings/{showingId}/seats/{seatId}/holds" to "reserveSeat",
                "post /showings/{showingId}/seats/{seatId}/bookings" to "confirmBooking",
                "post /showings/{showingId}/holds" to "reserveSeatGroup",
                "post /showings/{showingId}/holds/status" to "getSeatGroupHold",
                "post /showings/{showingId}/holds/release" to "releaseSeatGroup",
                "post /showings/{showingId}/bookings" to "confirmSeatGroupBooking",
                "post /admin/showings" to "createShowing",
                "put /admin/showings/{showingId}" to "updateShowing",
                "delete /admin/showings/{showingId}" to "deleteShowing",
            ),
            operations.mapValues { (_, operation) -> operation.required("operationId").stringValue() },
        )

        val booking = operations.getValue("post /showings/{showingId}/seats/{seatId}/bookings")
        val groupBooking = operations.getValue("post /showings/{showingId}/bookings")
        val confirmationSchema = "#/components/schemas/ConfirmBookingRequest"
        for (operation in listOf(booking, groupBooking)) {
            assertEquals(confirmationSchema, operation.at("/requestBody/content/application~1json/schema/\$ref").stringValue())
            assertEquals(setOf("200", "201", "400", "404", "409"), operation.required("responses").propertyNames().toSet())
        }
        val request = spec.at(confirmationSchema.removePrefix("#"))
        val required = setOf("holdId", "name", "birthDate", "email", "termsAccepted", "privacyAccepted")
        assertEquals(required, request.required("required").values().map { it.stringValue() }.toSet())
        assertEquals(required + setOf("phone", "newsletterOptIn"), request.required("properties").propertyNames().toSet())
        assertEquals("uuid", request.at("/properties/holdId/format").stringValue())
        assertEquals("date", request.at("/properties/birthDate/format").stringValue())
        for (consent in listOf("termsAccepted", "privacyAccepted")) {
            val property = request.at("/properties/$consent")
            assertEquals("boolean", property.required("type").stringValue())
            assertEquals(json.readTree("true"), property.required("const"))
        }
        assertEquals(setOf("string", "null"), request.at("/properties/phone/type").values().map { it.stringValue() }.toSet())
        assertEquals(setOf("boolean", "null"), request.at("/properties/newsletterOptIn/type").values().map { it.stringValue() }.toSet())
        val seatMap = operations.getValue("get /showings/{showingId}/seats")
        assertTrue(seatMap.at("/responses/503/content").has("application/problem+json"))

        val groupHoldStatuses = mapOf(
            "/holds" to "201",
            "/holds/status" to "200",
            "/holds/release" to "204",
        )
        for ((suffix, success) in groupHoldStatuses) {
            val operation = operations.getValue("post /showings/{showingId}$suffix")
            assertEquals(setOf(success, "400", "404", "409"), operation.required("responses").propertyNames().toSet())
        }
        val release = operations.getValue("post /showings/{showingId}/holds/release")
        assertFalse(release.at("/responses/204").has("content"))

        val responseSchemas = spec.at("/components/schemas")
        assertEquals("uri-reference", responseSchemas.at("/ProblemDetail/properties/instance/format").stringValue())
        for ((path, requestSchema) in mapOf(
            "/seats/{seatId}/holds" to "ReserveSeatRequest",
            "/holds" to "ReserveSeatGroupRequest",
        )) {
            val requestBody = operations.getValue("post /showings/{showingId}$path").required("requestBody")
            assertTrue(requestBody.required("required").booleanValue())
            assertEquals(
                "#/components/schemas/$requestSchema",
                requestBody.at("/content/application~1json/schema/\$ref").stringValue(),
            )
            val schema = responseSchemas.required(requestSchema)
            assertTrue(schema.required("required").values().any { it.stringValue() == "expectedShowingRevision" })
            assertEquals(1, schema.at("/properties/expectedShowingRevision/minimum").intValue())
        }
        val requiredResponseFields = mapOf(
            "BookingResponse" to setOf("bookingId"),
            "SeatHoldResponse" to setOf("holdId", "expiresAt"),
            "SeatHoldGroupResponse" to setOf("holdId", "seatIds", "expiresAt", "bookingId"),
            "SeatMapView" to setOf("showingId", "seats"),
            "SeatView" to setOf("seatId", "status"),
            "ShowingView" to setOf("showingId", "movieTitle", "startsAt", "revision"),
        )
        for ((name, fields) in requiredResponseFields) {
            val schema = responseSchemas.required(name)
            assertEquals(fields, schema.required("required").values().map { it.stringValue() }.toSet(), name)
        }
        assertEquals(
            setOf("string", "null"),
            responseSchemas.at("/SeatHoldGroupResponse/properties/bookingId/type").values().map { it.stringValue() }.toSet(),
        )
        val seatIdsSchema = responseSchemas.at("/ReserveSeatGroupRequest/properties/seatIds")
        assertEquals(1, seatIdsSchema.required("minItems").intValue())
        assertTrue(seatIdsSchema.required("uniqueItems").booleanValue())

        for ((route, operation) in operations) {
            if ("/admin/" in route) {
                assertEquals(setOf("adminBearer"), operation.at("/security/0").propertyNames().toSet())
                assertEquals("#/components/responses/AdminUnauthorized", operation.at("/responses/401/\$ref").stringValue())
            } else {
                assertFalse(operation.has("security"))
            }
        }
        val bearer = spec.at("/components/securitySchemes/adminBearer")
        assertEquals("http", bearer.required("type").stringValue())
        assertEquals("bearer", bearer.required("scheme").stringValue())
        assertFalse(bearer.has("bearerFormat"))
        val unauthorized = spec.at("/components/responses/AdminUnauthorized")
        assertTrue(unauthorized.required("headers").has("WWW-Authenticate"))
        assertFalse(unauthorized.has("content"))
        assertFalse(operations.getValue("delete /admin/showings/{showingId}").at("/responses/204").has("content"))
    }

    @Test
    fun `the programme lists films and showtimes in chronological order`() {
        assertEquals(
            listOf(
                ShowingView(2, "The Lantern House", Instant.parse("2027-01-15T15:00:00Z"), 1),
                ShowingView(1, "Cinema Preview", Instant.parse("2027-01-15T18:30:00Z"), 1),
                ShowingView(3, "Cinema Preview", Instant.parse("2027-01-16T18:30:00Z"), 1),
            ),
            programme(),
        )

        jdbc.sql(
            """
            insert into showings (id, movie_title, starts_at)
            select 0, movie_title, starts_at from showings where id = 1
            """.trimIndent(),
        ).update()
        try {
            assertEquals(listOf(2L, 0L, 1L, 3L), programme().map { it.showingId })
        } finally {
            jdbc.sql("delete from showings where id = 0").update()
        }
    }

    @Test
    fun `group confirmation books every seat and recovers its receipt after expiry`() {
        clock.set(Instant.parse("2026-11-06T12:00:00Z"))
        val group = groupHoldJson(postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(21, 20))), 201)
        val holdId = UUID.fromString(group.required("holdId").stringValue())
        val credential = mapOf("holdId" to holdId.toString())
        assertEquals(listOf(20L, 21L), groupSeatIds(group))
        assertTrue(group.required("bookingId").isNull)
        assertEquals(group, groupHoldJson(postJson("/showings/1/holds/status", credential), 200))

        val memberHolds = jdbc.sql("select hold_id from show_seats where showing_id = 1 and seat_id in (20, 21) order by seat_id")
            .query(UUID::class.java).list().map { checkNotNull(it) }
        assertEquals(2, memberHolds.toSet().size)
        assertFalse(holdId in memberHolds)
        assertEquals(409, book(20, bookingPayload(memberHolds.first())).statusCode())
        listOf(20L, 21L).forEach { assertSeatStatus(it, "HELD") }

        val receiptId = bookingId(postJson("/showings/1/bookings", bookingPayload(holdId)), 201)
        listOf(20L, 21L).forEach { assertSeatStatus(it, "BOOKED") }
        assertEquals(
            memberHolds,
            jdbc.sql("select hold_id from seat_bookings where showing_id = 1 and seat_id in (20, 21) order by seat_id")
                .query(UUID::class.java).list(),
        )
        val memberBookings = jdbc.sql("select booking_id from seat_bookings where showing_id = 1 and seat_id in (20, 21)")
            .query(UUID::class.java).list()
        assertEquals(2, memberBookings.toSet().size)
        assertFalse(receiptId in memberBookings)
        assertEquals(409, book(20, bookingPayload(memberHolds.first())).statusCode())

        clock.set(Instant.parse(group.required("expiresAt").stringValue()))
        val receipt = groupHoldJson(postJson("/showings/1/holds/status", credential), 200)
        assertEquals(groupSeatIds(group), groupSeatIds(receipt))
        assertEquals(receiptId.toString(), receipt.required("bookingId").stringValue())
        assertEquals(receiptId, bookingId(postJson("/showings/1/bookings", bookingPayload(holdId)), 200))
        assertEquals(409, postJson("/showings/1/holds/release", credential).statusCode())
    }

    @Test
    fun `a group request never holds a subset and rejects invalid seat IDs`() {
        clock.set(Instant.parse("2026-11-06T13:00:00Z"))
        createdHold(hold(31))
        assertEquals(409, postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(30, 31))).statusCode())
        assertSeatStatus(30, "AVAILABLE")
        assertEquals(404, postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(30, 201))).statusCode())
        assertSeatStatus(30, "AVAILABLE")
        assertEquals(404, postJson("/showings/999/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(30))).statusCode())

        for (invalid in listOf(emptyList<Any>(), listOf(30, 30), listOf(0), listOf("30"), listOf(30.5))) {
            assertEquals(400, postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to invalid)).statusCode())
        }
        assertEquals(400, postJson("/showings/1/holds", emptyMap<String, Any>()).statusCode())
        assertEquals(400, postJson("/showings/1/holds/status", mapOf("holdId" to "invalid")).statusCode())
        assertEquals(404, postJson("/showings/1/bookings", bookingPayload(UUID.randomUUID())).statusCode())
        assertSeatStatus(30, "AVAILABLE")
    }

    @Test
    fun `overlapping groups with reversed input order have one complete winner`() {
        clock.set(Instant.parse("2026-11-06T14:00:00Z"))
        val contenders = listOf(listOf(42, 40, 41), listOf(43, 41, 40))
        val requests = withSeatLocked(40) {
            val pending = contenders.map { seats ->
                client.sendAsync(jsonRequest("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to seats)), HttpResponse.BodyHandlers.ofString())
            }
            awaitSeatLockWaiters(2)
            pending
        }

        val responses = requests.map { it.get(10, TimeUnit.SECONDS) }.sortedBy { it.statusCode() }
        val winner = groupHoldJson(responses.first(), 201)
        assertEquals(409, responses.last().statusCode(), responses.last().body())
        val winnerSeats = groupSeatIds(winner)
        assertEquals(3, winnerSeats.size)
        winnerSeats.forEach { assertSeatStatus(it, "HELD") }
        val losingSeat = (listOf(42L, 43L) - winnerSeats.toSet()).single()
        assertSeatStatus(losingSeat, "AVAILABLE")
    }

    @Test
    fun `releasing a stale group cannot release replacement holds`() {
        clock.set(Instant.parse("2026-11-06T15:00:00Z"))
        val original = groupHoldJson(postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(50, 51))), 201)
        val holdId = UUID.fromString(original.required("holdId").stringValue())
        val credential = mapOf("holdId" to holdId.toString())
        assertEquals(404, postJson("/showings/2/holds/status", credential).statusCode())
        assertEquals(404, postJson("/showings/2/holds/release", credential).statusCode())
        assertEquals(404, postJson("/showings/2/bookings", bookingPayload(holdId)).statusCode())
        assertSeatStatus(50, "HELD")

        clock.set(Instant.parse(original.required("expiresAt").stringValue()))
        assertEquals(409, postJson("/showings/1/holds/status", credential).statusCode())
        assertEquals(409, postJson("/showings/1/bookings", bookingPayload(holdId)).statusCode())
        val replacement = groupHoldJson(postJson("/showings/1/holds", mapOf("expectedShowingRevision" to 1, "seatIds" to listOf(50, 52))), 201)
        val replacementCredential = mapOf("holdId" to replacement.required("holdId").stringValue())
        repeat(2) { assertEquals(204, postJson("/showings/1/holds/release", credential).statusCode()) }
        assertEquals(replacement, groupHoldJson(postJson("/showings/1/holds/status", replacementCredential), 200))
        listOf(50L, 52L).forEach { assertSeatStatus(it, "HELD") }
        assertSeatStatus(51, "AVAILABLE")

        repeat(2) { assertEquals(204, postJson("/showings/1/holds/release", replacementCredential).statusCode()) }
        assertEquals(409, postJson("/showings/1/holds/status", replacementCredential).statusCode())
        listOf(50L, 52L).forEach { assertSeatStatus(it, "AVAILABLE") }
    }

    @Test
    fun `each new showing in the programme has its own reservable seats`() {
        clock.set(Instant.parse("2026-11-06T11:00:00Z"))
        val showingIds = programme().map { it.showingId }.filter { it != 1L }
        assertEquals(listOf(2L, 3L), showingIds)

        val holds = showingIds.map { showingId ->
            assertSeatStatus(1, "AVAILABLE", showingId)
            createdHold(hold(1, showingId))
        }
        assertNotEquals(holds[0].holdId, holds[1].holdId)
        showingIds.forEach { assertSeatStatus(1, "HELD", it) }
    }

    @Test
    fun `both hold routes require a positive integer showing revision`() {
        val paths = listOf(
            "/showings/1/seats/32/holds" to emptyMap<String, Any?>(),
            "/showings/1/holds" to mapOf("seatIds" to listOf(32, 33)),
        )
        for ((path, fields) in paths) {
            assertEquals(400, postJson(path, fields).statusCode())
            for (revision in listOf(null, 0, -1, 1.5, "1", true, Int.MAX_VALUE.toLong() + 1)) {
                assertEquals(400, postJson(path, fields + ("expectedShowingRevision" to revision)).statusCode())
            }
        }
        assertSeatStatus(32, "AVAILABLE")
        assertSeatStatus(33, "AVAILABLE")
    }

    @Test
    fun `only one contender can hold a seat when the previous hold expires during the lock wait`() {
        clock.set(Instant.parse("2026-11-06T12:00:00Z"))
        val previousHold = createdHold(hold(1))
        clock.set(previousHold.expiresAt.minusNanos(1))

        val requests = withSeatLocked(1) {
            val contenders = List(2) {
                client.sendAsync(holdRequest(1), HttpResponse.BodyHandlers.ofString())
            }
            awaitSeatLockWaiters(2)
            clock.set(previousHold.expiresAt)
            contenders
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
    fun `a seat map read spanning hold expiry retries and sees the committed booking`() {
        clock.set(Instant.parse("2026-11-06T18:00:00Z"))
        val hold = createdHold(hold(7))
        clock.set(hold.expiresAt.minusNanos(1))
        val reads = AtomicInteger()

        seatMapProbe.interceptReads({ seats ->
            if (reads.incrementAndGet() == 1) {
                assertEquals(
                    SeatAvailabilitySnapshot(7, hold.expiresAt, booked = false),
                    seats.single { it.seatId == 7L },
                )
                bookingId(book(7, bookingPayload(hold.holdId)), 201)
                clock.set(hold.expiresAt)
            }
            seats
        }) {
            assertSeatStatus(7, "BOOKED")
        }

        assertEquals(2, reads.get())
    }

    @Test
    fun `a seat map request fails with 503 after three snapshots spanning expiry`() {
        clock.set(Instant.parse("2026-11-06T19:00:00Z"))
        val reads = AtomicInteger()

        val response = seatMapProbe.interceptReads({
            reads.incrementAndGet()
            val expiresAt = clock.instant().plusSeconds(1)
            clock.set(expiresAt)
            listOf(SeatAvailabilitySnapshot(1, expiresAt, booked = false))
        }) {
            get("/showings/1/seats")
        }

        assertEquals(503, response.statusCode(), response.body())
        assertEquals(3, reads.get())
    }

    @Test
    fun `unknown showings and seats return not found`() {
        assertEquals(404, get("/showings/999/seats").statusCode())
        assertEquals(404, hold(1, showingId = 999).statusCode())
        assertEquals(404, hold(201).statusCode())
        val payload = bookingPayload(UUID.randomUUID())
        assertEquals(404, book(1, payload, showingId = 999).statusCode())
        assertEquals(404, book(201, payload).statusCode())
    }

    @Test
    fun `a confirmed seat remains booked and a replay preserves the original booking`() {
        clock.set(Instant.parse("2026-11-06T14:00:00.123456789Z"))
        val hold = createdHold(hold(3))
        val original = bookingPayload(hold.holdId) + mapOf("phone" to "+49 123 456", "newsletterOptIn" to true)
        val createdBookingId = bookingId(book(3, original), 201)
        assertSeatStatus(3, "BOOKED")

        clock.set(hold.expiresAt.plusSeconds(1))
        val changed = bookingPayload(hold.holdId) + mapOf(
            "name" to "Different Name",
            "birthDate" to "2015-06-07",
            "email" to "different@example.test",
        )
        assertEquals(createdBookingId, bookingId(book(3, changed), 200))
        assertEquals(409, book(3, bookingPayload(UUID.randomUUID())).statusCode())
        assertEquals(409, hold(3).statusCode())
        assertSeatStatus(3, "BOOKED")

        val stored = storedBooking(3)
        assertEquals(createdBookingId, stored["booking_id"])
        assertEquals(hold.holdId, stored["hold_id"])
        assertEquals(original["name"], stored["customer_name"])
        assertEquals(original["birthDate"], stored["birth_date"].toString())
        assertEquals(original["email"], stored["email"])
        assertEquals(original["phone"], stored["phone"])
        assertEquals(true, stored["terms_accepted"])
        assertEquals(true, stored["privacy_accepted"])
        assertEquals(true, stored["newsletter_opt_in"])
        val seat = jdbc.sql("select hold_id, hold_expires_at from show_seats where showing_id = 1 and seat_id = 3")
            .query().singleRow()
        assertEquals(null, seat["hold_id"])
        assertEquals(null, seat["hold_expires_at"])
    }

    @Test
    fun `confirmation rejects holds at expiry or after replacement`() {
        clock.set(Instant.parse("2026-11-06T15:00:00Z"))
        val previousHold = createdHold(hold(4))
        clock.set(previousHold.expiresAt.minusNanos(1))
        val body = json.writeValueAsString(bookingPayload(previousHold.holdId))
        val confirmation = withSeatLocked(4) {
            val pending = client.sendAsync(bookingRequest(4, body), HttpResponse.BodyHandlers.ofString())
            awaitSeatLockWaiters(1)
            clock.set(previousHold.expiresAt)
            pending
        }.get(10, TimeUnit.SECONDS)
        assertEquals(409, confirmation.statusCode())
        assertEquals(
            0L,
            jdbc.sql("select count(*) from seat_bookings where showing_id = 1 and seat_id = 4")
                .query(Long::class.javaObjectType).single(),
        )
        assertSeatStatus(4, "AVAILABLE")

        val currentHold = createdHold(hold(4))
        assertEquals(409, book(4, bookingPayload(previousHold.holdId)).statusCode())
        assertEquals(409, book(4, bookingPayload(UUID.randomUUID())).statusCode())
        assertSeatStatus(4, "HELD")
        bookingId(book(4, bookingPayload(currentHold.holdId)), 201)
        assertSeatStatus(4, "BOOKED")
    }

    @Test
    fun `new bookings validate the birth date against Berlin time after the seat lock`() {
        val berlinMidnight = Instant.parse("2026-11-06T23:00:00Z")
        clock.set(berlinMidnight.minusNanos(1))
        val hold = createdHold(hold(8))
        val payload = bookingPayload(hold.holdId)
        val futureDate = payload + ("birthDate" to "2026-11-08")
        val rejected = book(8, futureDate)
        assertEquals(400, rejected.statusCode(), rejected.body())
        assertEquals("Birth date cannot be in the future.", json.readTree(rejected.body()).required("detail").stringValue())
        assertEquals(409, book(8, futureDate + ("holdId" to UUID.randomUUID().toString())).statusCode())
        assertSeatStatus(8, "HELD")

        val corrected = json.writeValueAsString(payload + ("birthDate" to "2026-11-07"))
        val confirmed = withSeatLocked(8) {
            val pending = client.sendAsync(bookingRequest(8, corrected), HttpResponse.BodyHandlers.ofString())
            awaitSeatLockWaiters(1)
            clock.set(berlinMidnight)
            pending
        }.get(10, TimeUnit.SECONDS)
        val created = bookingId(confirmed, 201)
        assertEquals("2026-11-07", storedBooking(8)["birth_date"].toString())
        assertEquals(created, bookingId(book(8, futureDate), 200))
    }

    @Test
    fun `concurrent confirmation of the same hold creates one booking`() {
        clock.set(Instant.parse("2026-11-06T16:00:00Z"))
        val hold = createdHold(hold(5))
        val body = json.writeValueAsString(bookingPayload(hold.holdId))

        val requests = withSeatLocked(5) {
            val contenders = List(2) {
                client.sendAsync(bookingRequest(5, body), HttpResponse.BodyHandlers.ofString())
            }
            awaitSeatLockWaiters(2)
            contenders
        }

        val responses = requests.map { it.get(10, TimeUnit.SECONDS) }.sortedBy { it.statusCode() }
        val replayed = bookingId(responses[0], 200)
        val created = bookingId(responses[1], 201)
        assertEquals(created, replayed)
        val stored = storedBooking(5)
        assertEquals(created, stored["booking_id"])
        assertEquals(hold.holdId, stored["hold_id"])
        assertEquals(null, stored["phone"])
        assertEquals(false, stored["newsletter_opt_in"])
        assertSeatStatus(5, "BOOKED")
    }

    @Test
    fun `invalid booking inputs return 400 without consuming the hold or exposing submitted values`() {
        clock.set(Instant.parse("2026-11-06T17:00:00Z"))
        val hold = createdHold(hold(6))
        val valid = bookingPayload(hold.holdId)
        val invalid = listOf(
            valid - "holdId",
            valid - "name",
            valid - "birthDate",
            valid - "email",
            valid - "termsAccepted",
            valid - "privacyAccepted",
            valid + ("holdId" to "not-a-uuid"),
            valid + ("birthDate" to "2001-02-29"),
            valid + ("email" to "invalid-email"),
            valid + ("name" to " "),
            valid + ("name" to 17),
            valid + ("name" to "x".repeat(201)),
            valid + ("name" to "contains\u0000nul"),
            valid + ("termsAccepted" to false),
            valid + ("privacyAccepted" to false),
            valid + ("privacyAccepted" to "true"),
            valid + ("newsletterOptIn" to 1),
            valid + ("phone" to "x".repeat(51)),
        ).map { json.writeValueAsString(it) } + "{\"name\":\"Ada Example\""

        for (body in invalid) {
            val response = client.send(bookingRequest(6, body), HttpResponse.BodyHandlers.ofString())
            assertEquals(400, response.statusCode(), response.body())
            assertEquals("Invalid booking request.", json.readTree(response.body()).required("detail").stringValue())
            assertTrue("Ada Example" !in response.body())
            assertTrue("ada@example.test" !in response.body())
        }

        assertSeatStatus(6, "HELD")
        bookingId(book(6, valid), 201)
    }

    private fun <T> withSeatLocked(seatId: Long, action: () -> T): T = dataSource.connection.use { gate ->
        gate.autoCommit = false
        try {
            gate.prepareStatement(
                "select 1 from show_seats where showing_id = 1 and seat_id = ? for update",
            ).use { statement ->
                statement.setLong(1, seatId)
                statement.executeQuery().use { rows -> assertTrue(rows.next()) }
            }
            action()
        } finally {
            gate.rollback()
        }
    }

    private fun awaitSeatLockWaiters(expected: Int) {
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
                        if (rows.getInt(1) == expected) return
                    }
                    Thread.sleep(20)
                }
            }
        }
        fail("Expected $expected requests waiting for the PostgreSQL seat lock.")
    }

    private fun hold(seatId: Long, showingId: Long = 1): HttpResponse<String> =
        client.send(holdRequest(seatId, showingId), HttpResponse.BodyHandlers.ofString())

    private fun holdRequest(seatId: Long, showingId: Long = 1): HttpRequest =
        jsonRequest("/showings/$showingId/seats/$seatId/holds", mapOf("expectedShowingRevision" to 1))

    private fun bookingPayload(holdId: UUID): Map<String, Any> = mapOf(
        "holdId" to holdId.toString(),
        "name" to "Ada Example",
        "birthDate" to "1990-02-03",
        "email" to "ada@example.test",
        "termsAccepted" to true,
        "privacyAccepted" to true,
    )

    private fun book(seatId: Long, payload: Map<String, Any>, showingId: Long = 1): HttpResponse<String> =
        client.send(
            bookingRequest(seatId, json.writeValueAsString(payload), showingId),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun bookingRequest(seatId: Long, body: String, showingId: Long = 1): HttpRequest =
        request("/showings/$showingId/seats/$seatId/bookings")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

    private fun postJson(path: String, payload: Any): HttpResponse<String> =
        client.send(jsonRequest(path, payload), HttpResponse.BodyHandlers.ofString())

    private fun jsonRequest(path: String, payload: Any): HttpRequest =
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build()

    private fun groupHoldJson(response: HttpResponse<String>, expectedStatus: Int): JsonNode {
        assertEquals(expectedStatus, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(setOf("holdId", "seatIds", "expiresAt", "bookingId"), body.propertyNames().toSet())
        return body
    }

    private fun groupSeatIds(group: JsonNode): List<Long> =
        group.required("seatIds").values().map { it.longValue() }

    private fun bookingId(response: HttpResponse<String>, expectedStatus: Int): UUID {
        assertEquals(expectedStatus, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(1, body.size())
        return UUID.fromString(body.required("bookingId").stringValue())
    }

    private fun storedBooking(seatId: Long): Map<String, Any?> =
        jdbc.sql("select * from seat_bookings where showing_id = 1 and seat_id = :seatId")
            .param("seatId", seatId)
            .query()
            .singleRow()

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

    private fun programme(): List<ShowingView> {
        val response = get("/showings")
        assertEquals(200, response.statusCode(), response.body())
        val showings = json.readTree(response.body())
        assertTrue(showings.isArray)
        return showings.values().map { showing ->
            assertEquals(4, showing.size())
            ShowingView(
                showingId = showing.required("showingId").longValue(),
                movieTitle = showing.required("movieTitle").stringValue(),
                startsAt = Instant.parse(showing.required("startsAt").stringValue()),
                revision = showing.required("revision").intValue(),
            )
        }
    }

    private fun assertSeatStatus(seatId: Long, expectedStatus: String, showingId: Long = 1) {
        val response = get("/showings/$showingId/seats")
        assertEquals(200, response.statusCode(), response.body())
        val seatMap = json.readTree(response.body())
        assertEquals(showingId, seatMap.required("showingId").longValue())
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
    class SeatMapProbeConfiguration {
        @Bean
        @Primary
        fun seatMapProbe(@Qualifier("jdbcSeatMapReader") delegate: SeatMapReader): SeatMapProbe =
            SeatMapProbe(delegate)
    }

    class SeatMapProbe(private val delegate: SeatMapReader) : SeatMapReader {
        private val interceptor =
            AtomicReference<((List<SeatAvailabilitySnapshot>) -> List<SeatAvailabilitySnapshot>)?>()

        fun <T> interceptReads(
            transform: (List<SeatAvailabilitySnapshot>) -> List<SeatAvailabilitySnapshot>,
            read: () -> T,
        ): T {
            check(interceptor.compareAndSet(null, transform))
            return try {
                read()
            } finally {
                interceptor.set(null)
            }
        }

        override fun findSeats(showingId: Long): List<SeatAvailabilitySnapshot>? {
            val seats = delegate.findSeats(showingId) ?: return null
            return interceptor.get()?.invoke(seats) ?: seats
        }
    }
}
