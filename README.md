# Mini Doodle — Meeting Scheduling & Availability Service

A high-performance backend service built with **Java 21**, **Spring Boot 4.2.0**, and **PostgreSQL 16** that enables users to manage time slots, schedule meetings safely under concurrent load with full idempotency support, and query aggregated availability across both **REST** and **GraphQL** APIs.

---

## 1. Requirement Traceability (Task Specification vs. Implementation)

Every requirement from the challenge specification is mapped below to its concrete architectural and code implementation:

| Challenge Requirement (Verbatim) | How It Was Fulfilled |
| :--- | :--- |
| *"Your task is to design and implement a simplified version of the Doodle platform — a meeting scheduling platform."* | Implemented as a self-contained Spring Boot 4.2.0 service (`com.minidoodle`) supporting time-slot lifecycle management, idempotent meeting booking, participant tracking, and aggregated availability summaries. |
| *"As a user, I should be able to create time slots in my calendar, modify or delete them, and mark them as busy or free."* | Exposed via **REST** (`POST`, `PATCH`, `DELETE /api/v1/users/{userId}/slots`) and **GraphQL** (`createTimeSlot`, `updateTimeSlot`, `deleteTimeSlot`). Slots transition freely between `FREE` and `BUSY`, while `BOOKED` slots are protected against mutation or deletion. |
| *"Users should also be able to schedule meetings in available time slots, providing details like the meeting's title, description, and participants."* | Exposed via `POST /api/v1/slots/{slotId}/meetings` (REST, with optional `Idempotency-Key` header) and `bookMeeting` (GraphQL, with optional `idempotencyKey` input). Atomically transitions a `FREE` slot to `BOOKED` and persists the `Meeting` with its title, description, and validated participant email list. |
| *"Users should also be able to query available and busy time slots within a given time frame, with aggregated information to visualize their availability."* | Exposed via `GET /api/v1/users/{userId}/availability?from=...&to=...&status=...` (REST) and `userAvailability` (GraphQL). Returns `totalFreeMinutes`, `totalBusyMinutes`, `totalBookedMinutes` (accurately clipped to the requested window boundaries), and the chronological list of matching slots. |
| *"The application should use a relational database to store users, time slots, and meetings, with a schema optimized for handling hundreds of users and thousands of time slots."* | Backed by **PostgreSQL 16** with **Flyway** migrations (`V1__init_schema.sql`, `V2__seed_data.sql`). Uses a composite B-tree index `(user_id, start_time, end_time, status)` for fast window queries, half-open `[start, end)` GiST exclusion constraints to prevent overlapping slots at the DB level, `UNIQUE` idempotency keys on `meetings`, and `@BatchMapping` in GraphQL to eliminate N+1 queries. |
| *"Ensure the platform can handle high concurrency, especially when multiple users attempt to book the same time slot simultaneously."* | Protected with **JPA Optimistic Locking (`@Version`)** on `TimeSlotEntity` inside an atomic transaction boundary. When distinct users race for the same slot, one succeeds (`201 Created`) and concurrent losers fail fast with `409 Conflict` (REST) / `CONFLICT` error extension (GraphQL). When the same client retries with an `Idempotency-Key`, the existing meeting is safely replayed (`200 OK`). |
| *"Remember that Calendar is just a domain concept; do not leak it outside the domain."* | `Calendar` exists strictly as the **Aggregate Root** inside `com.minidoodle.domain.model`. It encapsulates all overlap, status-transition, and booking invariants, and is never exposed in JPA entities, REST paths/DTOs, or the GraphQL schema. |
| *"Create a git repository for the project; your first commit should include a skeleton README.md file."* | Structured with clean, incremental Git commits starting from an initial skeleton `README.md`. |
| *"Document your design decisions, architecture, and any trade-offs in the README file."* | Documented in Sections 2 and 3 below. |
| *"Include instructions on how to set up and run the application using Docker Compose."* | Provided in Section 4 (`docker-compose up --build` and optional `--profile observability` mode). |
| *"Implement tests to ensure the correctness of your API endpoints."* | Verified by **12 unit, repository slice, and Testcontainers integration test classes** covering domain invariants, cross-user authorization, PostgreSQL GiST constraint translation, GraphQL `@BatchMapping`, 5-thread concurrent booking races, idempotent retries, and OpenTelemetry spans/metrics. |
| *"Using Java and Spring Boot is a must."* | Built on **Java 21** and **Spring Boot 4.2.0**. |
| *"Extra points for metrics, observability, and API documentation."* | Instrumented with **OpenTelemetry** (custom spans & business counters), **Spring Boot Actuator / Prometheus**, interactive **Swagger UI** (`/swagger-ui.html`), and **GraphiQL** (`/graphiql`). |

---

## 2. Architecture & Design Decisions

### Hexagonal / Clean Layered Architecture
```text
com.minidoodle
├── domain          # Pure Java 21 business logic (zero Spring/JPA dependencies)
│   ├── model       # Calendar (Aggregate Root), TimeSlot, Meeting, TimeRange, AvailabilitySummary
│   ├── exception   # DomainException, SlotOverlapException, InvalidSlotOperationException
│   └── repository  # Outbound Ports (CalendarRepository, MeetingRepository)
├── application     # Transactional Use Cases shared by both REST & GraphQL
│   ├── command     # CreateTimeSlotCommand, UpdateTimeSlotCommand, BookMeetingCommand
│   └── service     # TimeSlotApplicationService, MeetingApplicationService, AvailabilityApplicationService
├── persistence     # Outbound PostgreSQL / Spring Data JPA Adapters
│   ├── entity      # TimeSlotEntity (@Version), MeetingEntity (idempotencyKey, requestHash)
│   ├── jpa         # Indexed range & batch lookup queries
│   └── adapter     # Maps Calendar <-> Entities & translates GiST violations to SlotOverlapException
└── api             # Inbound Transport Adapters
    ├── rest        # REST Controllers, Jakarta Validation DTOs & GlobalRestExceptionHandler
    └── graphql     # GraphQL Resolvers, Extended Scalars, @BatchMapping & GlobalGraphQlExceptionHandler
```

### Key Technical Trade-offs
1. **Dual-Layer Overlap Prevention (Domain + PostgreSQL GiST):**
  * `Calendar` validates half-open `[start, end)` intervals in memory (allowing back-to-back slots such as `09:00–10:00` and `10:00–11:00`).
  * PostgreSQL enforces `EXCLUDE USING gist (user_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)` so even concurrent slot-creation requests cannot create overlapping intervals.
2. **Optimistic Locking (`@Version`) vs. Pessimistic Locking (`SELECT FOR UPDATE`):**
  * Chose **optimistic locking** on `TimeSlotEntity` because reads (`userAvailability`) heavily outnumber writes (`bookMeeting`), avoiding row-level lock contention while guaranteeing atomic single-winner booking semantics under bursts.
3. **Retry-Safe Idempotent Booking (`Idempotency-Key` + SHA-256 Request Hash):**
  * Clients can pass an optional `Idempotency-Key` header (REST) or `idempotencyKey` input field (GraphQL).
  * The service computes a canonical SHA-256 `request_hash` (`slotId` + `title` + `description` + sorted `participantEmails`). Identical retries replay the existing `Meeting` (`200 OK` in REST), simultaneous duplicate retries recover the winner's `Meeting`, and key reuse with a modified payload is rejected with `409 Conflict`.
4. **Window-Sliced Aggregate Loading:**
  * Instead of loading a user's entire multi-year history into `Calendar`, `loadByUserIdAndWindow` queries only slots intersecting `[from, to)` via the composite index `(user_id, start_time, end_time, status)`.
5. **GraphQL N+1 Prevention (`@BatchMapping`):**
  * Resolving `userAvailability { slots { meeting { ... } } }` uses `@BatchMapping` in `MeetingGraphQlController` to fetch all meetings in a single `WHERE slot_id IN (...)` query rather than issuing one SQL query per slot.

---

## 3. Quickstart with Docker Compose

### Prerequisites
* **Docker** and **Docker Compose** (or **Java 21** + Docker to run `./mvnw test` locally).

### Mode A: Standard Startup (PostgreSQL + Backend)
Starts PostgreSQL 16, applies Flyway migrations (`V1__init_schema.sql` and `V2__seed_data.sql`), and launches the Spring Boot service with OTLP network exporters disabled so no collector is required:

```bash
docker-compose up --build
```

### Mode B: Full Observability Startup (PostgreSQL + Backend + Grafana LGTM)
Starts the application alongside **`grafana/otel-lgtm`** (Grafana + Tempo + Loki + Mimir + OpenTelemetry Collector) with live trace, metric, and log exporting enabled:

```bash
OTEL_TRACING_ENABLED=true OTEL_METRICS_ENABLED=true OTEL_LOGGING_ENABLED=true docker-compose --profile observability up --build
```

### Available Endpoints & Playgrounds
* **REST API Base URL:** `http://localhost:8080/api/v1`
* **Swagger UI (Interactive REST Docs):** `http://localhost:8080/swagger-ui.html`
* **GraphQL Endpoint:** `http://localhost:8080/graphql`
* **GraphiQL Playground:** `http://localhost:8080/graphiql`
* **Actuator Health & Metrics:** `http://localhost:8080/actuator/health` | `http://localhost:8080/actuator/metrics`
* **Grafana UI (in `observability` mode):** `http://localhost:3000` (anonymous admin enabled)

---

## 4. Sample API Requests (Using Pre-Seeded Data)

`V2__seed_data.sql` seeds three users and tomorrow's UTC schedule automatically on startup:
* **Alice (`11111111-1111-1111-1111-111111111111`):**
  * `aaaa1111-1111-1111-1111-111111111111` (`09:00–10:00` UTC, `BOOKED`)
  * `aaaa2222-2222-2222-2222-222222222222` (`10:00–11:00` UTC, `FREE` — ready to book)
  * `aaaa3333-3333-3333-3333-333333333333` (`11:30–12:30` UTC, `BUSY`)
  * `aaaa4444-4444-4444-4444-444444444444` (`14:00–15:30` UTC, `FREE`)

* **Bob (`22222222-2222-2222-2222-222222222222`):** `bbbb1111-...` (`10:00–11:00` FREE), `bbbb2222-...` (`13:00–14:00` FREE)
* **Carol (`33333333-3333-3333-3333-333333333333`):** Empty schedule

### REST Examples (`curl`)

#### 1. Query Aggregated Availability
```bash
curl -s "http://localhost:8080/api/v1/users/11111111-1111-1111-1111-111111111111/availability?from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z" | jq
```

#### 2. Book Alice's Free Slot (`10:00–11:00` UTC)
```bash
curl -i -X POST "http://localhost:8080/api/v1/slots/aaaa2222-2222-2222-2222-222222222222/meetings" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-booking-001" \
  -d '{
    "title": "Product Sync",
    "description": "Align on Q1 roadmap",
    "participantEmails": ["alice@minidoodle.dev", "bob@minidoodle.dev"]
  }'

```

#### 3. Create a New Time Slot for Carol
```bash
curl -i -X POST "http://localhost:8080/api/v1/users/33333333-3333-3333-3333-333333333333/slots" \
  -H "Content-Type: application/json" \
  -d '{
    "startTime": "2026-12-15T10:00:00Z",
    "endTime": "2026-12-15T11:00:00Z"
  }'

```

#### 4. Mark a Slot as BUSY
```bash
curl -i -X PATCH "http://localhost:8080/api/v1/users/22222222-2222-2222-2222-222222222222/slots/bbbb1111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"status": "BUSY"}'
```

### GraphQL Examples (http://localhost:8080/graphiql)


#### 1. Query Availability with Nested Meetings
```
query {
  userAvailability(
    userId: "11111111-1111-1111-1111-111111111111"
    from: "2026-01-01T00:00:00Z"
    to: "2027-01-01T00:00:00Z"
  ) {
    totalFreeMinutes
    totalBusyMinutes
    totalBookedMinutes
    slots {
      id
      startTime
      endTime
      status
      meeting {
        title
        description
        participantEmails
      }
    }
  }
}
```
#### 2. Book a Meeting Mutation
```
mutation {
  bookMeeting(input: {
    slotId: "bbbb2222-2222-2222-2222-222222222222"
    title: "GraphQL Demo Sync"
    description: "Scheduled via GraphQL mutation"
    participants: ["bob@minidoodle.dev", "carol@minidoodle.dev"]
    idempotencyKey: "gql-demo-booking-001"
  }) {
    id
    title
    participantEmails
    createdAt
  }
}
```

## 5.  Observability & Telemetry (OpenTelemetry + Grafana)

* **Custom Business Spans:** `TimeSlotApplicationService` and `MeetingApplicationService` emit `doodle.slot.create` (tagged with `user.id`) and `doodle.meeting.book` (tagged with `slot.id`, `participants.count`, and `idempotent`), correlated automatically with HTTP/GraphQL and JDBC spans.
* **Custom Business Counters:**
  * `doodle.slots.created` — total time slots created
  * `doodle.meetings.booked` — total meetings scheduled
  * `doodle.meetings.conflicts` — total optimistic lock booking conflicts prevented
  * `doodle.meetings.idempotent_replays` — total duplicate retry requests served idempotently
* **Inspecting Live Telemetry in Grafana (`--profile observability`):**
  1. Open `http://localhost:3000` → **Explore**.
  2. **Traces:** Select **Tempo** → search by Service Name `MiniDoodle` or Span Name `doodle.meeting.book`.
  3. **Metrics:** Select **Prometheus** → query `doodle_meetings_booked_total`, `doodle_meetings_conflicts_total`, `doodle_meetings_idempotent_replays_total`, or `doodle_slots_created_total`.
  4. **Logs:** Select **Loki** → filter by `service_name="MiniDoodle"` to view logs linked to trace IDs.

## 6.  Test Suite Structure

* **Domain Unit Tests (`com.minidoodle.domain.model`):** `TimeRangeTest`, `MeetingTest`, `TimeSlotTest`, `CalendarTest`
* **Application Service Unit Tests (`com.minidoodle.application.service`):** `ApplicationServiceUnitTest`
* **Persistence Slice Tests (`com.minidoodle.persistence`):** `JpaRepositoryAdapterIT` (`@DataJpaTest` + Testcontainers verifying PostgreSQL GiST exclusion constraint translation and idempotency key lookups)
* **Full-Stack Integration Tests (`com.minidoodle.integration`):**
  * `TimeSlotRestIT` — REST CRUD, Bean Validation, status filters, and error codes (`400`, `404`, `409`)
  * `TimeSlotGraphQlIT` — GraphQL queries/mutations, `@BatchMapping` nested meeting resolution, and error extensions
  * `ConcurrencyBookingIT` — 5-thread `CyclicBarrier` race test verifying single-winner booking under contention
  * `IdempotentBookingIT` — Verifies retry replay (`200 OK`), mismatched payload rejection (`409 Conflict`), and simultaneous duplicate retry convergence
  * `OpenTelemetryIT` — Verifies custom OpenTelemetry spans and Micrometer counters via `InMemorySpanExporter`

