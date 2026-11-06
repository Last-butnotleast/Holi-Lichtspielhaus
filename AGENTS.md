# Cinema Booking

Build the cinema reservation kata on the existing Kotlin and Spring Boot starter. Keep one Gradle module with a `booking` business capability under `de.holisticon.cinema`.

## Coding principles

- **Modularity** gives each capability and invariant a clear owner.
- **Editability** keeps related changes together and makes their effects easy to assess.
- **Debuggability** makes control flow, state transitions, and data flow traceable through meaningful names and typed failures.

Give each function one clear responsibility. Compose focused functions into readable use cases whose phases reflect the booking flow. Use the domain's language consistently, especially when distinguishing a showing, a seat, a temporary hold, and a confirmed booking.

Keep files cohesive around a use case or invariant. Give validation and transformations a clear owner. Split files when responsibilities or reasons to change diverge. Introduce abstractions when they clarify a real boundary or simplify a caller.

For behavior-preserving refactors, preserve public contracts, failure types and their precedence, deterministic ordering, expiry semantics, transaction atomicity, and retry behavior. Run the existing tests that cover those contracts. Add targeted regressions for uncovered behavior.

Keep source files free of handwritten comments, including inline and documentation comments. Preserve comments added by generators. Use clear names and types in code, and put explanations that need to persist in project documentation.

## Architecture

- Keep booking rules in plain Kotlin under `booking/domain`. A `ShowSeat` owns the hold and confirmed booking for one seat in one showing. Use its methods to change that state.
- Put use cases in `booking/application`, grouped by reservation, confirmation, and queries. The application coordinates persistence and time. Keep the aggregate repository contract with the domain and query ports with the application.
- Place HTTP mapping and request validation in `booking/adapter/inbound/web`. Place JPA entities, mappings, queries, and locking in `booking/adapter/outbound/persistence`. Adapters depend on core contracts. Use `booking/configuration` to wire dependencies.
- Use separate command and query models over the same PostgreSQL database. Queries return views for showing selection and the seat map. Apply the same expiry rule on both paths.
- Keep seat updates atomic within a short command transaction. A hold expires when the current time reaches its deadline. Use an injectable clock and evaluate expiry after any write lock wait. Confirmation must match the current hold and preserve a single booking on retry.

## MVP scope

Keep the implementation focused on showing selection, temporary seat holds, and booking confirmation. Use Flyway for schema changes and a small seed programme. Extend the scaffold as responsibilities become concrete. Add features and infrastructure when the kata or the user calls for them.

Mirror production packages in tests. Exercise state transitions and expiry with a controlled clock. Verify concurrent attempts for the same seat against PostgreSQL using Testcontainers.

## Design references

- [Eric Evans, Domain-Driven Design Reference](https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf)
- [Vaughn Vernon, Effective Aggregate Design, Part I](https://www.dddcommunity.org/wp-content/uploads/files/pdf_articles/Vernon_2011_1.pdf)
- [Alistair Cockburn, Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture)
- [Martin Fowler, CQRS](https://martinfowler.com/bliki/CQRS.html)

## Agent workflow

The primary agent coordinates the work and keeps its context focused. Delegate inspection, substantive implementation, and independent review to agents with clear ownership, acceptance criteria, and concise handoffs. Keep trivial edits with the primary agent.

Before substantive engineering work, use the installed `cognee:memory` skill to query the `software_engineering` dataset (`39afdfe2-e6f3-500a-ae40-b13833cf99ff`). Use focused, read-only searches and keep capture disabled. Pass relevant records in handoffs. A fresh parent brief can satisfy recall for a delegated task.
