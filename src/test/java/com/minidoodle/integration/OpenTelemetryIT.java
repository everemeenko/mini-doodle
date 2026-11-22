package com.minidoodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.minidoodle.api.rest.dto.BookMeetingRequest;
import com.minidoodle.api.rest.dto.CreateSlotRequest;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(OpenTelemetryIT.OtelTestConfig.class)
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
class OpenTelemetryIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class OtelTestConfig {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SpanProcessor inMemorySpanProcessor(InMemorySpanExporter exporter) {
            return SimpleSpanProcessor.create(exporter);
        }
    }

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void resetSpans() {
        spanExporter.reset();
    }

    @Test
    void shouldRecordOtelSpansAndMetricsWhenCreatingSlotAndBookingMeeting() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(40).withHour(10).withMinute(0).withSecond(0).withNano(0);

        TimeSlotResponse createdSlot = restClient.post()
                .uri("/api/v1/users/{userId}/slots", userId)
                .body(new CreateSlotRequest(start, start.plusHours(1)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TimeSlotResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(createdSlot).isNotNull();

        restClient.post()
                .uri("/api/v1/slots/{slotId}/meetings", createdSlot.id())
                .body(new BookMeetingRequest("OTel Verified Sync", "Testing traces", Set.of("bob@minidoodle.dev")))
                .exchange()
                .expectStatus().isCreated();

        assertThat(meterRegistry.get("doodle.slots.created").counter().count()).isGreaterThanOrEqualTo(1.0);
        assertThat(meterRegistry.get("doodle.meetings.booked").counter().count()).isGreaterThanOrEqualTo(1.0);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans)
                .extracting(SpanData::getName)
                .contains("doodle.slot.create", "doodle.meeting.book");
    }
}
