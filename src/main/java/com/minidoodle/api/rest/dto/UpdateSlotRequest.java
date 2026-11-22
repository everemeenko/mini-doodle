package com.minidoodle.api.rest.dto;

import com.minidoodle.domain.model.SlotStatus;
import jakarta.validation.constraints.AssertTrue;
import java.time.OffsetDateTime;

public record UpdateSlotRequest(
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        SlotStatus status
) {
    @AssertTrue(message = "Both startTime and endTime must be provided together when updating time bounds")
    public boolean isTimeRangeComplete() {
        return (startTime == null) == (endTime == null);
    }
}
