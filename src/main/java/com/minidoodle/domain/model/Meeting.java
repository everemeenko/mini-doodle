package com.minidoodle.domain.model;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Meeting {
    private final UUID id;
    private final UUID slotId;
    private final String title;
    private final String description;
    private final Set<String> participantEmails;
    private final OffsetDateTime createdAt;

    public Meeting(UUID id, UUID slotId, String title, String description,
                   Set<String> participantEmails, OffsetDateTime createdAt) {
        if (title == null || title.isBlank()) {
            throw new InvalidSlotOperationException("Meeting title cannot be blank");
        }
        if (participantEmails == null || participantEmails.isEmpty()) {
            throw new InvalidSlotOperationException("Meeting must have at least one participant");
        }
        this.id = Objects.requireNonNull(id);
        this.slotId = Objects.requireNonNull(slotId);
        this.title = title;
        this.description = description;
        this.participantEmails = Collections.unmodifiableSet(new HashSet<>(participantEmails));
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() { return id; }
    public UUID getSlotId() { return slotId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Set<String> getParticipantEmails() { return participantEmails; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}