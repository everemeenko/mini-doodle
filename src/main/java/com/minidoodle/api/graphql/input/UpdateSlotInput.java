package com.minidoodle.api.graphql.input;

import com.minidoodle.domain.model.SlotStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateSlotInput(
    UUID slotId,
    UUID userId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    SlotStatus status
) {}
