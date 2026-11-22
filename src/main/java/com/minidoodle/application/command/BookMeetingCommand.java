package com.minidoodle.application.command;

import java.util.Set;
import java.util.UUID;

public record BookMeetingCommand(
        UUID slotId,
        String title,
        String description,
        Set<String> participantEmails,
        String idempotencyKey
) {
    public BookMeetingCommand(UUID slotId, String title, String description, Set<String> participantEmails) {
        this(slotId, title, description, participantEmails, null);
    }
}
