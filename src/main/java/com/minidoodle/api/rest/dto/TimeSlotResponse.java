package com.minidoodle.api.rest.dto;

import com.minidoodle.domain.model.SlotStatus;
import com.minidoodle.domain.model.TimeSlot;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TimeSlotResponse(
    UUID id,
    UUID userId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    SlotStatus status
) {
    public static TimeSlotResponse from(TimeSlot slot) {
        return new TimeSlotResponse(
            slot.getId(),
            slot.getUserId(),
            slot.getTimeRange().start(),
            slot.getTimeRange().end(),
            slot.getStatus()
        );
    }
}
