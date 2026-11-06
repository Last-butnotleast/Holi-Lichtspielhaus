# Holi Lichtspielhaus

Choose a film and showing, reserve seats for five minutes, and confirm your booking.

## Run locally with Docker

From the repository root, with Docker and Docker Compose v2.22 or later running:

```sh
docker compose up --build --wait
```

Open the [cinema](http://127.0.0.1:5173/) to try the seeded programme. The [backend](http://127.0.0.1:8080/showings) and [OpenAPI spec](http://127.0.0.1:8080/v3/api-docs.yaml) are also available on localhost. Vite proxies browser requests from `/api` to Spring Boot. Compose waits for PostgreSQL before starting the backend, and Spring Boot applies the Flyway migrations on startup.

If you already use the default ports, set `CINEMA_FRONTEND_PORT` and `CINEMA_BACKEND_PORT` in your shell or in a root `.env` file. The browser's API URL stays relative when the ports change.

For development with file watching, run `docker compose up --build --watch`. Frontend source edits use Vite hot reload. Backend source and build file edits rebuild and recreate the backend container. Frontend dependency changes rebuild the frontend container.

Use `docker compose logs -f` to follow the logs and `docker compose down` to stop the stack while keeping the database. To reset local bookings and reseed the programme, use `docker compose down --volumes` before starting it again.

PostgreSQL is on the Compose network. To open a SQL shell, run `docker compose exec db psql -U cinema -d cinema`. Set `CINEMA_DB_PASSWORD` before the first start to override the local development password. To enable the optional admin API, set `CINEMA_ADMIN_TOKEN` and send it in the `Authorization: Bearer` header.

## Architecture

The Kotlin backend keeps booking rules in the domain, coordinates transactions in use cases, and connects HTTP and PostgreSQL through adapters. The React frontend organizes the programme and booking flow by feature and uses types generated from the backend's OpenAPI contract.

The architecture diagram walks through the guest flow, concurrent holds, and the test setup. [PNG export](docs/holisticon-combined.png) · [Editable Excalidraw source](docs/holisticon-combined.excalidraw)

## Run checks and refresh the API contract

The backend suite uses Java 25 and starts PostgreSQL through Testcontainers. The frontend build uses Node.js 24 and pnpm 10.10.0.

```sh
./gradlew test
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

With the updated backend running on port 8080, refresh the committed OpenAPI spec and TypeScript transport types from the repository root.

```sh
curl -fsS http://127.0.0.1:8080/v3/api-docs.yaml -o docs/openapi.yaml
cd frontend
pnpm generate:api
```
