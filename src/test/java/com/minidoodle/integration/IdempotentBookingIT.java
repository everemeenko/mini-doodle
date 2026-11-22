package com.minidoodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.minidoodle.api.rest.dto.BookMeetingRequest;
import com.minidoodle.api.rest.dto.CreateSlotRequest;
import com.minidoodle.api.rest.dto.MeetingResponse;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

class IdempotentBookingIT extends AbstractIntegrationTest {

    private static final UUID CAROL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldReplaySameMeetingOnRetryAndRejectMismatchedPayloadForSameKey() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(70).withHour(10).withMinute(0).withSecond(0).withNano(0);

        TimeSlotResponse slot = restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(start, start.plusHours(1)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(slot).isNotNull();

        String idemKey = "booking-key-" + UUID.randomUUID();
        BookMeetingRequest request = new BookMeetingRequest(
                "Idempotent Sync", "Retry-safe booking", Set.of("carol@minidoodle.dev", "alice@minidoodle.dev")
        );

        // 1. Initial request -> 201 Created
        MeetingResponse firstResponse = restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot.id())
                .header("Idempotency-Key", idemKey)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(MeetingResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(firstResponse).isNotNull();

        // 2. Identical retry (with participants in different Set order) -> 200 OK with exact same Meeting ID
        BookMeetingRequest reorderedParticipantsRequest = new BookMeetingRequest(
                "Idempotent Sync", "Retry-safe booking", Set.of("alice@minidoodle.dev", "carol@minidoodle.dev")
        );

        MeetingResponse replayResponse = restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot.id())
                .header("Idempotency-Key", idemKey)
                .body(reorderedParticipantsRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeetingResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(replayResponse).isNotNull();
        assertThat(replayResponse.id()).isEqualTo(firstResponse.id());

        // 3. Reusing the same Idempotency-Key with a DIFFERENT title -> 409 Conflict
        restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot.id())
                .header("Idempotency-Key", idemKey)
                .body(new BookMeetingRequest("Tampered Title", "Retry-safe booking", Set.of("carol@minidoodle.dev")))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void shouldReturnSameMeetingWhenSimultaneousDuplicateRetriesRaceWithSameKey() throws Exception {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(71).withHour(14).withMinute(0).withSecond(0).withNano(0);

        TimeSlotResponse slot = restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(start, start.plusHours(1)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(slot).isNotNull();

        String sharedKey = "concurrent-retry-" + UUID.randomUUID();
        BookMeetingRequest identicalPayload = new BookMeetingRequest(
                "Simultaneous Retry Meeting", "Notes", Set.of("carol@minidoodle.dev")
        );

        int threads = 5;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<MeetingResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                RestTestClient client = RestTestClient.bindToServer()
                        .baseUrl("http://localhost:" + port)
                        .build();
                barrier.await();
                return client.post()
                        .uri("/api/v1/slots/{slotId}/meetings", slot.id())
                        .header("Idempotency-Key", sharedKey)
                        .body(identicalPayload)
                        .exchange()
                        .expectStatus().is2xxSuccessful()
                        .expectBody(MeetingResponse.class)
                        .returnResult()
                        .getResponseBody();
            });
        }

        List<MeetingResponse> responses = new ArrayList<>();
        for (Future<MeetingResponse> future : executor.invokeAll(tasks)) {
            responses.add(future.get());
        }
        executor.shutdown();

        // All 5 duplicate retries succeed and receive the exact same Meeting instance
        assertThat(responses)
                .extracting(MeetingResponse::id)
                .containsOnly(responses.getFirst().id());
    }
}

