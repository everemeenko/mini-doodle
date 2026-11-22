package com.minidoodle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimeSlotTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 12, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldCreateFreeSlotWithNullVersion() {
        TimeRange range = new TimeRange(BASE, BASE.plusHours(1));
        TimeSlot slot = TimeSlot.createFree(USER_ID, range);

        assertThat(slot.getId()).isNotNull();
        assertThat(slot.getUserId()).isEqualTo(USER_ID);
        assertThat(slot.getTimeRange()).isEqualTo(range);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.getVersion()).isNull();
    }

    @Test
    void shouldToggleBetweenFreeAndBusyWhenNotBooked() {
        TimeSlot slot = TimeSlot.createFree(USER_ID, new TimeRange(BASE, BASE.plusHours(1)));

        slot.changeStatus(SlotStatus.BUSY);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);

        slot.changeStatus(SlotStatus.FREE);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void shouldRejectSettingStatusDirectlyToBooked() {
        TimeSlot slot = TimeSlot.createFree(USER_ID, new TimeRange(BASE, BASE.plusHours(1)));

        assertThatThrownBy(() -> slot.changeStatus(SlotStatus.BOOKED))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("bookMeeting");
    }

    @Test
    void shouldBookFreeSlotAndPreventFurtherModificationOrDeletion() {
        TimeSlot slot = TimeSlot.createFree(USER_ID, new TimeRange(BASE, BASE.plusHours(1)));

        Meeting meeting = slot.book("Planning", "Sprint goals", Set.of("alice@minidoodle.dev"));

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(meeting.getSlotId()).isEqualTo(slot.getId());
        assertThat(meeting.getTitle()).isEqualTo("Planning");

        assertThatThrownBy(() -> slot.book("Duplicate", null, Set.of("bob@minidoodle.dev")))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("Only FREE slots");

        assertThatThrownBy(() -> slot.reschedule(new TimeRange(BASE.plusHours(2), BASE.plusHours(3))))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("booked slot");

        assertThatThrownBy(() -> slot.changeStatus(SlotStatus.FREE))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("booked slot");

        assertThatThrownBy(slot::ensureDeletable)
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("active meeting");
    }

    @Test
    void shouldRejectBookingBusySlotAndAllowDeletionOfFreeOrBusySlot() {
        TimeSlot slot = TimeSlot.createFree(USER_ID, new TimeRange(BASE, BASE.plusHours(1)));
        slot.changeStatus(SlotStatus.BUSY);

        assertThatThrownBy(() -> slot.book("Sync", null, Set.of("alice@minidoodle.dev")))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("BUSY");

        assertThatCode(slot::ensureDeletable).doesNotThrowAnyException();
    }
}
