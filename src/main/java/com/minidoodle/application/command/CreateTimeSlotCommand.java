package com.minidoodle.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTimeSlotCommand(
    UUID userId,
    OffsetDateTime startTime,
    OffsetDateTime endTime
) {}
