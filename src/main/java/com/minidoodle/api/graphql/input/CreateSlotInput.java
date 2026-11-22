package com.minidoodle.api.graphql.input;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSlotInput(
    UUID userId,
    OffsetDateTime startTime,
    OffsetDateTime endTime
) {}
