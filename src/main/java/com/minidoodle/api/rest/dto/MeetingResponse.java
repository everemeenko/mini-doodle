package com.minidoodle.api.rest.dto;

import com.minidoodle.domain.model.Meeting;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record MeetingResponse(
    UUID id,
    UUID slotId,
    String title,
    String description,
    Set<String> participantEmails,
    OffsetDateTime createdAt
) {
    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
            meeting.getId(),
            meeting.getSlotId(),
            meeting.getTitle(),
            meeting.getDescription(),
            meeting.getParticipantEmails(),
            meeting.getCreatedAt()
        );
    }
}
