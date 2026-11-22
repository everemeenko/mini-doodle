package com.minidoodle.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class MeetingEntity {

    @Id
    private UUID id;

    @Column(name = "slot_id", nullable = false, unique = true)
    private UUID slotId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "meeting_participants",
            joinColumns = @JoinColumn(name = "meeting_id")
    )
    @Column(name = "participant_email", nullable = false)
    private Set<String> participantEmails = new HashSet<>();

    protected MeetingEntity() {}

    public MeetingEntity(UUID id, UUID slotId, String title, String description,
                         String idempotencyKey, String requestHash,
                         Set<String> participantEmails, OffsetDateTime createdAt) {
        this.id = id;
        this.slotId = slotId;
        this.title = title;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.participantEmails = new HashSet<>(participantEmails);
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSlotId() { return slotId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Set<String> getParticipantEmails() { return participantEmails; }
}

