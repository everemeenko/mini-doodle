package com.minidoodle.api.graphql.input;

import java.util.Set;
import java.util.UUID;

public record BookMeetingInput(
        UUID slotId,
        String title,
        String description,
        Set<String> participants,
        String idempotencyKey
) {}
