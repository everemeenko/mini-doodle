package com.minidoodle.api.rest;

import com.minidoodle.api.rest.dto.CreateSlotRequest;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import com.minidoodle.api.rest.dto.UpdateSlotRequest;
import com.minidoodle.application.command.CreateTimeSlotCommand;
import com.minidoodle.application.command.UpdateTimeSlotCommand;
import com.minidoodle.application.service.TimeSlotApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/slots")
public class TimeSlotRestController {

    private final TimeSlotApplicationService timeSlotService;

    public TimeSlotRestController(TimeSlotApplicationService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeSlotResponse createSlot(@PathVariable UUID userId,
                                       @Valid @RequestBody CreateSlotRequest request) {
        var command = new CreateTimeSlotCommand(userId, request.startTime(), request.endTime());
        return TimeSlotResponse.from(timeSlotService.createSlot(command));
    }

    @PatchMapping("/{slotId}")
    public TimeSlotResponse updateSlot(@PathVariable UUID userId,
                                       @PathVariable UUID slotId,
                                       @Valid @RequestBody UpdateSlotRequest request) {
        var command = new UpdateTimeSlotCommand(
            userId, slotId, request.startTime(), request.endTime(), request.status()
        );
        return TimeSlotResponse.from(timeSlotService.updateSlot(command));
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable UUID userId, @PathVariable UUID slotId) {
        timeSlotService.deleteSlot(userId, slotId);
    }
}
