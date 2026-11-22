package com.minidoodle.api.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateSlotRequest(
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime
) {}
