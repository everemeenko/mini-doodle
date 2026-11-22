package com.minidoodle.api.graphql;

import com.minidoodle.api.graphql.input.CreateSlotInput;
import com.minidoodle.api.graphql.input.UpdateSlotInput;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import com.minidoodle.application.command.CreateTimeSlotCommand;
import com.minidoodle.application.command.UpdateTimeSlotCommand;
import com.minidoodle.application.service.TimeSlotApplicationService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import java.util.UUID;

@Controller
public class TimeSlotGraphQlController {

    private final TimeSlotApplicationService timeSlotService;

    public TimeSlotGraphQlController(TimeSlotApplicationService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @MutationMapping
    public TimeSlotResponse createTimeSlot(@Argument CreateSlotInput input) {
        var command = new CreateTimeSlotCommand(input.userId(), input.startTime(), input.endTime());
        return TimeSlotResponse.from(timeSlotService.createSlot(command));
    }

    @MutationMapping
    public TimeSlotResponse updateTimeSlot(@Argument UpdateSlotInput input) {
        var command = new UpdateTimeSlotCommand(
            input.userId(), input.slotId(), input.startTime(), input.endTime(), input.status()
        );
        return TimeSlotResponse.from(timeSlotService.updateSlot(command));
    }

    @MutationMapping
    public boolean deleteTimeSlot(@Argument UUID userId, @Argument UUID slotId) {
        timeSlotService.deleteSlot(userId, slotId);
        return true;
    }
}
