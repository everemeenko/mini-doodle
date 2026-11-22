package com.minidoodle.domain.model;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.exception.SlotOverlapException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Calendar {
    private final UUID userId;
    private final List<TimeSlot> slots;

    public Calendar(UUID userId, List<TimeSlot> existingSlots) {
        this.userId = Objects.requireNonNull(userId);
        this.slots = new ArrayList<>(existingSlots != null ? existingSlots : List.of());
    }

    public TimeSlot addSlot(TimeRange range) {
        validateNoOverlap(range, null);
        TimeSlot newSlot = TimeSlot.createFree(this.userId, range);
        this.slots.add(newSlot);
        return newSlot;
    }

    public TimeSlot updateSlot(UUID slotId, TimeRange newRange, SlotStatus newStatus) {
        TimeSlot slot = findSlotOrThrow(slotId);

        if (newRange != null && !newRange.equals(slot.getTimeRange())) {
            validateNoOverlap(newRange, slotId);
            slot.reschedule(newRange);
        }

        if (newStatus != null && newStatus != slot.getStatus()) {
            slot.changeStatus(newStatus);
        }

        return slot;
    }

    public void removeSlot(UUID slotId) {
        TimeSlot slot = findSlotOrThrow(slotId);
        slot.ensureDeletable();
        this.slots.remove(slot);
    }

    public Meeting scheduleMeeting(UUID slotId, String title, String description, java.util.Set<String> participants) {
        TimeSlot slot = findSlotOrThrow(slotId);
        return slot.book(title, description, participants);
    }

    public AvailabilitySummary calculateAvailability(TimeRange window, SlotStatus filterStatus) {
        List<TimeSlot> matchingSlots = slots.stream()
                .filter(s -> s.getTimeRange().overlaps(window))
                .filter(s -> filterStatus == null || s.getStatus() == filterStatus)
                .sorted(Comparator.comparing(s -> s.getTimeRange().start()))
                .toList();

        long freeMins = 0;
        long busyMins = 0;
        long bookedMins = 0;

        for (TimeSlot slot : matchingSlots) {
            long mins = calculateClippedMinutes(slot.getTimeRange(), window);
            switch (slot.getStatus()) {
                case FREE -> freeMins += mins;
                case BUSY -> busyMins += mins;
                case BOOKED -> bookedMins += mins;
            }
        }

        return new AvailabilitySummary(userId, window, freeMins, busyMins, bookedMins, matchingSlots);
    }

    private long calculateClippedMinutes(TimeRange slotRange, TimeRange window) {
        var effectiveStart = slotRange.start().isAfter(window.start()) ? slotRange.start() : window.start();
        var effectiveEnd = slotRange.end().isBefore(window.end()) ? slotRange.end() : window.end();
        return new TimeRange(effectiveStart, effectiveEnd).durationMinutes();
    }

    private void validateNoOverlap(TimeRange candidate, UUID ignoreSlotId) {
        boolean hasOverlap = slots.stream()
                .filter(s -> !s.getId().equals(ignoreSlotId))
                .anyMatch(s -> s.getTimeRange().overlaps(candidate));

        if (hasOverlap) {
            throw new SlotOverlapException("Time slot overlaps with an existing slot in the user's calendar");
        }
    }

    private TimeSlot findSlotOrThrow(UUID slotId) {
        return slots.stream()
                .filter(s -> s.getId().equals(slotId))
                .findFirst()
                .orElseThrow(() -> new InvalidSlotOperationException("Time slot not found in calendar: " + slotId));
    }

    public UUID getUserId() { return userId; }
    public List<TimeSlot> getSlots() { return Collections.unmodifiableList(slots); }
}