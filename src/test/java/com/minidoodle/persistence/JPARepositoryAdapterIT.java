package com.minidoodle.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minidoodle.domain.exception.SlotOverlapException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.Meeting;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;
import com.minidoodle.domain.repository.MeetingRepository.StoredIdempotentMeeting;
import com.minidoodle.persistence.adapter.JpaCalendarRepositoryAdapter;
import com.minidoodle.persistence.adapter.JpaMeetingRepositoryAdapter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaCalendarRepositoryAdapter.class, JpaMeetingRepositoryAdapter.class})
@Testcontainers
class JPARepositoryAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JpaCalendarRepositoryAdapter calendarAdapter;

    @Autowired
    private JpaMeetingRepositoryAdapter meetingAdapter;

    // Seeded user Alice from V2__seed_data.sql
    private static final UUID ALICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldTranslatePostgresGistOverlapConstraintIntoSlotOverlapException() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(60).withHour(10).withMinute(0).withSecond(0).withNano(0);

        // Save first slot in an isolated Calendar instance
        Calendar firstCalendar = new Calendar(ALICE_ID, List.of());
        firstCalendar.addSlot(new TimeRange(start, start.plusHours(1)));
        calendarAdapter.save(firstCalendar);

        // Bypass in-memory check using an empty Calendar instance to hit PostgreSQL EXCLUDE USING gist directly
        Calendar secondCalendar = new Calendar(ALICE_ID, List.of());
        secondCalendar.addSlot(new TimeRange(start.plusMinutes(30), start.plusMinutes(90)));

        assertThatThrownBy(() -> calendarAdapter.save(secondCalendar))
                .isInstanceOf(SlotOverlapException.class)
                .hasMessageContaining("Database prevented overlapping time slots");
    }

    @Test
    void shouldReturnEmptyOptionalWhenSlotIdDoesNotExist() {
        assertThat(calendarAdapter.loadBySlotId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldSaveAndLookupMeetingByIdempotencyKeyAndBatchSlotIds() {
        assertThat(meetingAdapter.findAllBySlotIds(List.of())).isEmpty();
        assertThat(meetingAdapter.findByIdempotencyKey("non-existent-key")).isEmpty();

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(61).withHour(11).withMinute(0).withSecond(0).withNano(0);
        Calendar calendar = new Calendar(ALICE_ID, List.of());
        TimeSlot slot = calendar.addSlot(new TimeRange(start, start.plusHours(1)));
        Meeting meeting = calendar.scheduleMeeting(slot.getId(), "Adapter Test", "Details", Set.of("alice@minidoodle.dev"));

        String idemKey = "adapter-idem-" + UUID.randomUUID();
        String expectedHash = "a".repeat(64);

        calendarAdapter.save(calendar);
        meetingAdapter.save(meeting, idemKey, expectedHash);

        // 1. Verify batch lookup by slotIds
        List<Meeting> found = meetingAdapter.findAllBySlotIds(List.of(slot.getId(), UUID.randomUUID()));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getTitle()).isEqualTo("Adapter Test");

        // 2. Verify lookup by idempotencyKey returns both Meeting and requestHash
        Optional<StoredIdempotentMeeting> stored = meetingAdapter.findByIdempotencyKey(idemKey);
        assertThat(stored).isPresent();
        assertThat(stored.get().meeting().getId()).isEqualTo(meeting.getId());
        assertThat(stored.get().requestHash()).isEqualTo(expectedHash);
    }
}
