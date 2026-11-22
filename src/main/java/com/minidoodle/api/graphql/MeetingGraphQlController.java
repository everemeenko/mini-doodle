package com.minidoodle.api.graphql;

import com.minidoodle.api.graphql.input.BookMeetingInput;
import com.minidoodle.api.rest.dto.MeetingResponse;
import com.minidoodle.api.rest.dto.TimeSlotResponse;
import com.minidoodle.application.command.BookMeetingCommand;
import com.minidoodle.application.service.MeetingApplicationService;
import com.minidoodle.domain.model.SlotStatus;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class MeetingGraphQlController {

    private final MeetingApplicationService meetingService;

    public MeetingGraphQlController(MeetingApplicationService meetingService) {
        this.meetingService = meetingService;
    }

    @MutationMapping
    public MeetingResponse bookMeeting(@Argument BookMeetingInput input) {
        var command = new BookMeetingCommand(
                input.slotId(), input.title(), input.description(), input.participants(), input.idempotencyKey()
        );
        return MeetingResponse.from(meetingService.bookMeeting(command).meeting());
    }

    @BatchMapping(typeName = "TimeSlot", field = "meeting")
    public Map<TimeSlotResponse, MeetingResponse> meetings(List<TimeSlotResponse> slots) {
        List<UUID> bookedSlotIds = slots.stream()
            .filter(s -> s.status() == SlotStatus.BOOKED)
            .map(TimeSlotResponse::id)
            .toList();

        var meetingsBySlotId = meetingService.findMeetingsBySlotIds(bookedSlotIds);

        Map<TimeSlotResponse, MeetingResponse> result = new HashMap<>();
        for (TimeSlotResponse slot : slots) {
            var meeting = meetingsBySlotId.get(slot.id());
            result.put(slot, meeting != null ? MeetingResponse.from(meeting) : null);
        }
        return result;
    }
}
