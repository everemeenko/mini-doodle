package com.minidoodle.api.rest;

import com.minidoodle.api.rest.dto.AvailabilityResponse;
import com.minidoodle.application.service.AvailabilityApplicationService;
import com.minidoodle.domain.model.SlotStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/availability")
public class AvailabilityRestController {

    private final AvailabilityApplicationService availabilityService;

    public AvailabilityRestController(AvailabilityApplicationService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public AvailabilityResponse getAvailability(
        @PathVariable UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(required = false) SlotStatus status
    ) {
        var summary = availabilityService.getUserAvailability(userId, from, to, status);
        return AvailabilityResponse.from(summary);
    }
}
