package com.minidoodle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.exception.SlotOverlapException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CalendarTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final OffsetDateTime DAY = OffsetDateTime.of(2026, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldAddBackToBackSlotsAndRejectOverlappingSlot() {
        Calendar calendar = new Calendar(USER_ID, List.of());

        TimeSlot first = calendar.addSlot(new TimeRange(DAY.plusHours(9), DAY.plusHours(10)));
        TimeSlot second = calendar.addSlot(new TimeRange(DAY.plusHours(10), DAY.plusHours(11)));

        assertThat(calendar.getSlots()).containsExactly(first, second);

        assertThatThrownBy(() -> calendar.addSlot(new TimeRange(DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30))))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    void shouldAllowReschedulingSlotIntoItsOwnPartiallyOverlappingWindowWithoutSelfConflict() {
        Calendar calendar = new Calendar(USER_ID, List.of());
        TimeSlot slot = calendar.addSlot(new TimeRange(DAY.plusHours(10), DAY.plusHours(11)));
        calendar.addSlot(new TimeRange(DAY.plusHours(12), DAY.plusHours(13)));

        // Shifting 10:00-11:00 to 10:30-11:30 overlaps its old self, which must be allowed
        TimeRange shifted = new TimeRange(DAY.plusHours(10).plusMinutes(30), DAY.plusHours(11).plusMinutes(30));
        TimeSlot updated = calendar.updateSlot(slot.getId(), shifted, SlotStatus.BUSY);

        assertThat(updated.getTimeRange()).isEqualTo(shifted);
        assertThat(updated.getStatus()).isEqualTo(SlotStatus.BUSY);

        // Shifting into 12:00-13:00 must fail
        assertThatThrownBy(() -> calendar.updateSlot(slot.getId(), new TimeRange(DAY.plusHours(12).plusMinutes(30), DAY.plusHours(13).plusMinutes(30)), null))
                .isInstanceOf(SlotOverlapException.class);
    }

    @Test
    void shouldRemoveUnbookedSlotAndRejectMissingOrBookedSlot() {
        Calendar calendar = new Calendar(USER_ID, List.of());
        TimeSlot freeSlot = calendar.addSlot(new TimeRange(DAY.plusHours(9), DAY.plusHours(10)));
        TimeSlot bookedSlot = calendar.addSlot(new TimeRange(DAY.plusHours(10), DAY.plusHours(11)));
        calendar.scheduleMeeting(bookedSlot.getId(), "1:1", null, Set.of("alice@minidoodle.dev"));

        calendar.removeSlot(freeSlot.getId());
        assertThat(calendar.getSlots()).containsExactly(bookedSlot);

        assertThatThrownBy(() -> calendar.removeSlot(bookedSlot.getId()))
                .isInstanceOf(InvalidSlotOperationException.class);

        assertThatThrownBy(() -> calendar.removeSlot(UUID.randomUUID()))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldCalculateClippedAvailabilityAndSupportStatusFilter() {
        Calendar calendar = new Calendar(USER_ID, List.of());

        // 08:30 - 10:00 FREE (only 09:00 - 10:00 = 60m falls inside [09:00, 13:00))
        calendar.addSlot(new TimeRange(DAY.plusHours(8).plusMinutes(30), DAY.plusHours(10)));

        // 10:00 - 11:00 BOOKED (60m inside window)
        TimeSlot booked = calendar.addSlot(new TimeRange(DAY.plusHours(10), DAY.plusHours(11)));
        calendar.scheduleMeeting(booked.getId(), "Design", null, Set.of("bob@minidoodle.dev"));

        // 11:00 - 12:00 BUSY (60m inside window)
        TimeSlot busy = calendar.addSlot(new TimeRange(DAY.plusHours(11), DAY.plusHours(12)));
        calendar.updateSlot(busy.getId(), null, SlotStatus.BUSY);

        // 12:30 - 14:00 FREE (only 12:30 - 13:00 = 30m falls inside [09:00, 13:00))
        calendar.addSlot(new TimeRange(DAY.plusHours(12).plusMinutes(30), DAY.plusHours(14)));

        // Outside window (15:00 - 16:00 FREE -> 0m)
        calendar.addSlot(new TimeRange(DAY.plusHours(15), DAY.plusHours(16)));

        TimeRange queryWindow = new TimeRange(DAY.plusHours(9), DAY.plusHours(13));

        AvailabilitySummary allSummary = calendar.calculateAvailability(queryWindow, null);
        assertThat(allSummary.totalFreeMinutes()).isEqualTo(90L);
        assertThat(allSummary.totalBookedMinutes()).isEqualTo(60L);
        assertThat(allSummary.totalBusyMinutes()).isEqualTo(60L);
        assertThat(allSummary.slots()).hasSize(4);

        AvailabilitySummary freeOnlySummary = calendar.calculateAvailability(queryWindow, SlotStatus.FREE);
        assertThat(freeOnlySummary.totalFreeMinutes()).isEqualTo(90L);
        assertThat(freeOnlySummary.totalBookedMinutes()).isZero();
        assertThat(freeOnlySummary.totalBusyMinutes()).isZero();
        assertThat(freeOnlySummary.slots()).hasSize(2);
    }
}