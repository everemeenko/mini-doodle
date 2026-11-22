package com.minidoodle.api.graphql.input;

import com.minidoodle.domain.model.SlotStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateSlotInput(
        @NotNull UUID slotId,
        @NotNull UUID userId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        SlotStatus status
) {
    @AssertTrue(message = "Both startTime and endTime must be provided together when updating time bounds")
    public boolean isTimeRangeComplete() {
        return (startTime == null) == (endTime == null);
    }
}
