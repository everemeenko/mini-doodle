package com.minidoodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.minidoodle.api.rest.dto.AvailabilityResponse;
import com.minidoodle.api.rest.dto.BookMeetingRequest;
import com.minidoodle.api.rest.dto.CreateSlotRequest;
import com.minidoodle.api.rest.dto.MeetingResponse;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import com.minidoodle.api.rest.dto.UpdateSlotRequest;
import com.minidoodle.domain.model.SlotStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeSlotRestIT extends AbstractIntegrationTest {

    // Seeded user Carol from V2__seed_data.sql (starts with 0 slots)
    private static final UUID CAROL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldSupportFullSlotAndMeetingLifecycleWithValidationAndFilters() {
        OffsetDateTime day = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0);

        // 1. Create two back-to-back FREE slots (09:00-10:00 and 10:00-11:00)
        TimeSlotResponse slot1 = restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(day, day.plusHours(1)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        TimeSlotResponse slot2 = restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(day.plusHours(1), day.plusHours(2)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(slot1).isNotNull();
        assertThat(slot2).isNotNull();

        // 2. Reject overlapping slot (09:30-10:30) with 409 Conflict
        restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(day.plusMinutes(30), day.plusMinutes(90)))
                .exchange()
                .expectStatus().isEqualTo(409);

        // 3. Reject invalid range (endTime <= startTime) with 400 Bad Request
        restClient.post()
                .uri("/api/v1/users/{userId}/slots", CAROL_ID)
                .body(new CreateSlotRequest(day.plusHours(3), day.plusHours(2)))
                .exchange()
                .expectStatus().isBadRequest();

        // 4. Reject invalid Bean Validation payloads (@NotBlank title, @Email participant) -> 400 Bad Request
        restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot1.id())
                .body(new BookMeetingRequest("   ", "Blank title", Set.of("alice@minidoodle.dev")))
                .exchange()
                .expectStatus().isBadRequest();

        restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot1.id())
                .body(new BookMeetingRequest("Valid Title", "Bad email", Set.of("not-an-email")))
                .exchange()
                .expectStatus().isBadRequest();

        // 5. Reject booking a non-existent slot -> 404 Not Found
        restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", UUID.randomUUID())
                .body(new BookMeetingRequest("Ghost Slot", "Notes", Set.of("alice@minidoodle.dev")))
                .exchange()
                .expectStatus().isNotFound();

        // 6. Toggle slot2 to BUSY
        TimeSlotResponse updatedSlot2 = restClient.patch()
                .uri("/api/v1/users/{userId}/slots/{slotId}", CAROL_ID, slot2.id())
                .body(new UpdateSlotRequest(null, null, SlotStatus.BUSY))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updatedSlot2).isNotNull();
        assertThat(updatedSlot2.status()).isEqualTo(SlotStatus.BUSY);

        // 7. Book slot1 into a Meeting
        MeetingResponse meeting = restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", slot1.id())
                .body(new BookMeetingRequest("1:1 Sync", "Quarterly goals", Set.of("carol@minidoodle.dev", "alice@minidoodle.dev")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(MeetingResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(meeting).isNotNull();
        assertThat(meeting.slotId()).isEqualTo(slot1.id());

        // 8. Attempt to delete BOOKED slot1 -> 400 Bad Request
        restClient.delete()
                .uri("/api/v1/users/{userId}/slots/{slotId}", CAROL_ID, slot1.id())
                .exchange()
                .expectStatus().isBadRequest();

        // 9. Query aggregated availability (all statuses vs filtered by BUSY)
        AvailabilityResponse fullSummary = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{userId}/availability")
                        .queryParam("from", day.toString())
                        .queryParam("to", day.plusHours(3).toString())
                        .build(CAROL_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AvailabilityResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(fullSummary).isNotNull();
        assertThat(fullSummary.totalBookedMinutes()).isEqualTo(60L);
        assertThat(fullSummary.totalBusyMinutes()).isEqualTo(60L);
        assertThat(fullSummary.totalFreeMinutes()).isZero();
        assertThat(fullSummary.slots()).hasSize(2);

        AvailabilityResponse busyOnly = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{userId}/availability")
                        .queryParam("from", day.toString())
                        .queryParam("to", day.plusHours(3).toString())
                        .queryParam("status", "BUSY")
                        .build(CAROL_ID))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AvailabilityResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(busyOnly).isNotNull();
        assertThat(busyOnly.slots()).hasSize(1);
        assertThat(busyOnly.slots().getFirst().id()).isEqualTo(slot2.id());

        // 10. Delete unbooked slot2 -> 204 No Content, and missing slot -> 404 Not Found
        restClient.delete()
                .uri("/api/v1/users/{userId}/slots/{slotId}", CAROL_ID, slot2.id())
                .exchange()
                .expectStatus().isNoContent();

        restClient.delete()
                .uri("/api/v1/users/{userId}/slots/{slotId}", CAROL_ID, UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }
}
