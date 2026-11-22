package com.minidoodle.persistence.entity;

import com.minidoodle.domain.model.SlotStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
public class TimeSlotEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlotStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TimeSlotEntity() {}

    public TimeSlotEntity(UUID id, UUID userId, OffsetDateTime startTime,
                          OffsetDateTime endTime, SlotStatus status, Long version) {
        this.id = id;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.version = version;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public SlotStatus getStatus() { return status; }
    public Long getVersion() { return version; }

    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
