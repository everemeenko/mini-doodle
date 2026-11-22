package com.minidoodle.api.graphql;

import com.minidoodle.api.rest.dto.AvailabilityResponse;
import com.minidoodle.application.service.AvailabilityApplicationService;
import com.minidoodle.domain.model.SlotStatus;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.time.OffsetDateTime;
import java.util.UUID;

@Controller
public class AvailabilityGraphQlController {

    private final AvailabilityApplicationService availabilityService;

    public AvailabilityGraphQlController(AvailabilityApplicationService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @QueryMapping
    public AvailabilityResponse userAvailability(
        @Argument UUID userId,
        @Argument OffsetDateTime from,
        @Argument OffsetDateTime to,
        @Argument SlotStatus status
    ) {
        var summary = availabilityService.getUserAvailability(userId, from, to, status);
        return AvailabilityResponse.from(summary);
    }
}
