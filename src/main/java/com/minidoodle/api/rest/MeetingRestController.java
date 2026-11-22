package com.minidoodle.api.rest;

import com.minidoodle.api.rest.dto.BookMeetingRequest;
import com.minidoodle.api.rest.dto.MeetingResponse;
import com.minidoodle.application.command.BookMeetingCommand;
import com.minidoodle.application.service.BookMeetingResult;
import com.minidoodle.application.service.MeetingApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/slots/{slotId}/meetings")
public class MeetingRestController {

    private final MeetingApplicationService meetingService;

    public MeetingRestController(MeetingApplicationService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    public ResponseEntity<MeetingResponse> bookMeeting(
            @PathVariable UUID slotId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookMeetingRequest request
    ) {
        var command = new BookMeetingCommand(
                slotId, request.title(), request.description(), request.participantEmails(), idempotencyKey
        );
        BookMeetingResult result = meetingService.bookMeeting(command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(MeetingResponse.from(result.meeting()));
    }
}
