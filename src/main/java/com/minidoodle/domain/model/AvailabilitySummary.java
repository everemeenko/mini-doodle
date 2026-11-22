package com.minidoodle.domain.model;

import java.util.List;
import java.util.UUID;

public record AvailabilitySummary(
        UUID userId,
        TimeRange window,
        long totalFreeMinutes,
        long totalBusyMinutes,
        long totalBookedMinutes,
        List<TimeSlot> slots
) {}