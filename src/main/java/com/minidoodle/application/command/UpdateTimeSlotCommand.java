package com.minidoodle.application.command;

import com.minidoodle.domain.model.SlotStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateTimeSlotCommand(
    UUID userId,
    UUID slotId,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    SlotStatus status
) {}
