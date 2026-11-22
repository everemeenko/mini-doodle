package com.minidoodle.api.rest.dto;

import com.minidoodle.domain.model.AvailabilitySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
    UUID userId,
    OffsetDateTime from,
    OffsetDateTime to,
    long totalFreeMinutes,
    long totalBusyMinutes,
    long totalBookedMinutes,
    List<TimeSlotResponse> slots
) {
    public static AvailabilityResponse from(AvailabilitySummary summary) {
        return new AvailabilityResponse(
            summary.userId(),
            summary.window().start(),
            summary.window().end(),
            summary.totalFreeMinutes(),
            summary.totalBusyMinutes(),
            summary.totalBookedMinutes(),
            summary.slots().stream().map(TimeSlotResponse::from).toList()
        );
    }
}
