package com.minidoodle.domain.model;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class TimeSlot {
    private final UUID id;
    private final UUID userId;
    private TimeRange timeRange;
    private SlotStatus status;
    private final Long version; // Для маппинга оптимистичной блокировки из Persistence-слоя

    public TimeSlot(UUID id, UUID userId, TimeRange timeRange, SlotStatus status, Long version) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.timeRange = Objects.requireNonNull(timeRange);
        this.status = Objects.requireNonNull(status);
        this.version = version;
    }

    public static TimeSlot createFree(UUID userId, TimeRange range) {
        return new TimeSlot(UUID.randomUUID(), userId, range, SlotStatus.FREE, null);
    }

    void reschedule(TimeRange newRange) {
        ensureNotBooked("Cannot reschedule a booked slot");
        this.timeRange = Objects.requireNonNull(newRange);
    }

    void changeStatus(SlotStatus newStatus) {
        ensureNotBooked("Cannot change status of a booked slot directly");
        if (newStatus == SlotStatus.BOOKED) {
            throw new InvalidSlotOperationException("Use bookMeeting() to transition a slot to BOOKED");
        }
        this.status = newStatus;
    }

    Meeting book(String title, String description, Set<String> participants) {
        if (this.status != SlotStatus.FREE) {
            throw new InvalidSlotOperationException("Only FREE slots can be booked. Current status: " + status);
        }
        this.status = SlotStatus.BOOKED;
        return new Meeting(UUID.randomUUID(), this.id, title, description, participants, OffsetDateTime.now());
    }

    void ensureDeletable() {
        ensureNotBooked("Cannot delete a slot that has an active meeting booked");
    }

    private void ensureNotBooked(String errorMessage) {
        if (this.status == SlotStatus.BOOKED) {
            throw new InvalidSlotOperationException(errorMessage);
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public TimeRange getTimeRange() { return timeRange; }
    public SlotStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}