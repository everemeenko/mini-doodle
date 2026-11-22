package com.minidoodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.minidoodle.api.rest.dto.BookMeetingRequest;
import com.minidoodle.api.rest.dto.CreateSlotRequest;
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

class ConcurrencyBookingIT extends AbstractIntegrationTest {

    private static final UUID BOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldAllowOnlyOneBookingWhenMultipleUsersRaceForSameSlot() throws Exception {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(30).withHour(16).withMinute(0).withSecond(0).withNano(0);

        TimeSlotResponse slot = restClient.post()
                .uri("/api/v1/users/{userId}/slots", BOB_ID)
                .body(new CreateSlotRequest(start, start.plusHours(1)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(slot).isNotNull();

        int threads = 5;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            tasks.add(() -> {
                RestTestClient client = RestTestClient.bindToServer()
                        .baseUrl("http://localhost:" + port)
                        .build();
                barrier.await(); // Release all 5 threads simultaneously
                return client.post()
                        .uri("/api/v1/slots/{slotId}/meetings", slot.id())
                        .body(new BookMeetingRequest(
                                "Race Meeting #" + index,
                                "Concurrent booking test",
                                Set.of("user" + index + "@minidoodle.dev")
                        ))
                        .exchange()
                        .returnResult(String.class)
                        .getStatus()
                        .value();
            });
        }

        List<Integer> statusCodes = new ArrayList<>();
        for (Future<Integer> future : executor.invokeAll(tasks)) {
            statusCodes.add(future.get());
        }
        executor.shutdown();

        long createdCount = statusCodes.stream().filter(code -> code == 201).count();
        long rejectedCount = statusCodes.stream().filter(code -> code == 409 || code == 400).count();

        assertThat(createdCount).isEqualTo(1L);
        assertThat(rejectedCount).isEqualTo(threads - 1L);
    }
}