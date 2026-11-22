package com.minidoodle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minidoodle.application.command.BookMeetingCommand;
import com.minidoodle.application.command.UpdateTimeSlotCommand;
import com.minidoodle.application.exception.IdempotencyConflictException;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.Meeting;
import com.minidoodle.domain.model.SlotStatus;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;
import com.minidoodle.domain.repository.CalendarRepository;
import com.minidoodle.domain.repository.MeetingRepository;
import com.minidoodle.domain.repository.MeetingRepository.StoredIdempotentMeeting;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;

class ApplicationServiceUnitTest {

    private CalendarRepository calendarRepository;
    private MeetingRepository meetingRepository;
    private SimpleMeterRegistry meterRegistry;
    private TimeSlotApplicationService timeSlotService;
    private MeetingApplicationService meetingService;

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 12, 10, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        calendarRepository = mock(CalendarRepository.class);
        meetingRepository = mock(MeetingRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        timeSlotService = new TimeSlotApplicationService(calendarRepository, observationRegistry, meterRegistry);
        meetingService = new MeetingApplicationService(
                calendarRepository, meetingRepository, observationRegistry, meterRegistry
        );
    }

    @Test
    void shouldRejectUpdateAndDeleteWhenSlotBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(UUID.randomUUID(), ownerId, new TimeRange(BASE, BASE.plusHours(1)), SlotStatus.FREE, 0L);
        Calendar ownerCalendar = new Calendar(ownerId, List.of(slot));

        when(calendarRepository.loadBySlotId(slot.getId())).thenReturn(Optional.of(ownerCalendar));

        assertThatThrownBy(() -> timeSlotService.updateSlot(
                new UpdateTimeSlotCommand(attackerId, slot.getId(), null, null, SlotStatus.BUSY)
        ))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("does not belong to user");

        assertThatThrownBy(() -> timeSlotService.deleteSlot(attackerId, slot.getId()))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("does not belong to user");

        verify(calendarRepository, never()).save(any());
        verify(calendarRepository, never()).deleteSlot(any());
    }

    @Test
    void shouldDelegateVersionedSlotToRepositoryOnDelete() {
        UUID ownerId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(UUID.randomUUID(), ownerId, new TimeRange(BASE, BASE.plusHours(1)), SlotStatus.FREE, 3L);
        Calendar ownerCalendar = new Calendar(ownerId, List.of(slot));

        when(calendarRepository.loadBySlotId(slot.getId())).thenReturn(Optional.of(ownerCalendar));

        timeSlotService.deleteSlot(ownerId, slot.getId());

        verify(calendarRepository).deleteSlot(slot);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUpdatingOrBookingMissingSlot() {
        UUID missingSlotId = UUID.randomUUID();
        when(calendarRepository.loadBySlotId(missingSlotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timeSlotService.updateSlot(
                new UpdateTimeSlotCommand(UUID.randomUUID(), missingSlotId, null, null, SlotStatus.BUSY)
        )).isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> meetingService.bookMeeting(
                new BookMeetingCommand(missingSlotId, "Sync", null, Set.of("a@minidoodle.dev"))
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldIncrementConflictCounterWhenOptimisticLockFailsDuringBooking() {
        UUID ownerId = UUID.randomUUID();
        TimeSlot slot = new TimeSlot(UUID.randomUUID(), ownerId, new TimeRange(BASE, BASE.plusHours(1)), SlotStatus.FREE, 0L);
        Calendar calendar = new Calendar(ownerId, List.of(slot));

        when(calendarRepository.loadBySlotId(slot.getId())).thenReturn(Optional.of(calendar));
        doThrow(new OptimisticLockingFailureException("Concurrent modification"))
                .when(calendarRepository).save(any());

        assertThatThrownBy(() -> meetingService.bookMeeting(
                new BookMeetingCommand(slot.getId(), "Race Sync", "Notes", Set.of("alice@minidoodle.dev"))
        )).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(meterRegistry.get("doodle.meetings.conflicts").counter().count()).isEqualTo(1.0);
        verify(meetingRepository, never()).save(any(), any(), any());
    }

    @Test
    void shouldAcquireAdvisoryLockBeforeCheckingExistingIdempotencyKey() {
        UUID slotId = UUID.randomUUID();
        Meeting existingMeeting = new Meeting(
                UUID.randomUUID(), slotId, "Original Sync", "Notes", Set.of("alice@minidoodle.dev"), BASE
        );

        when(meetingRepository.findByIdempotencyKey("idem-key-123"))
                .thenReturn(Optional.of(new StoredIdempotentMeeting(existingMeeting, "different-sha256-hash")));

        assertThatThrownBy(() -> meetingService.bookMeeting(
                new BookMeetingCommand(slotId, "Modified Sync", "Notes", Set.of("alice@minidoodle.dev"), "idem-key-123")
        ))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("different booking payload");

        InOrder order = inOrder(meetingRepository);
        order.verify(meetingRepository).acquireIdempotencyLock("idem-key-123");
        order.verify(meetingRepository).findByIdempotencyKey("idem-key-123");
        verify(calendarRepository, never()).loadBySlotId(any());
    }

    @Test
    void shouldMapMeetingsBySlotIdForBatchLookups() {
        UUID slot1 = UUID.randomUUID();
        UUID slot2 = UUID.randomUUID();
        Meeting meeting1 = new Meeting(UUID.randomUUID(), slot1, "M1", null, Set.of("a@minidoodle.dev"), BASE);
        Meeting meeting2 = new Meeting(UUID.randomUUID(), slot2, "M2", null, Set.of("b@minidoodle.dev"), BASE);

        when(meetingRepository.findAllBySlotIds(List.of(slot1, slot2))).thenReturn(List.of(meeting1, meeting2));

        Map<UUID, Meeting> result = meetingService.findMeetingsBySlotIds(List.of(slot1, slot2));

        assertThat(result).hasSize(2)
                .containsEntry(slot1, meeting1)
                .containsEntry(slot2, meeting2);
    }
}
