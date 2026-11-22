package com.minidoodle.api.graphql.input;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSlotInput(
        @NotNull UUID userId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime
) {}
