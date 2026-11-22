package com.minidoodle.api.rest.dto;

import com.minidoodle.domain.model.SlotStatus;
import java.time.OffsetDateTime;

public record UpdateSlotRequest(
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    SlotStatus status
) {}
