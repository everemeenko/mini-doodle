package com.minidoodle.domain.model;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

public record TimeRange(OffsetDateTime start, OffsetDateTime end) {

    public TimeRange {
        Objects.requireNonNull(start, "Start time must not be null");
        Objects.requireNonNull(end, "End time must not be null");
        if (!end.isAfter(start)) {
            throw new InvalidSlotOperationException("End time must be strictly after start time");
        }
    }

    public boolean overlaps(TimeRange other) {
        return this.start.isBefore(other.end) && this.end.isAfter(other.start);
    }

    public long durationMinutes() {
        return Duration.between(start, end).toMinutes();
    }
}