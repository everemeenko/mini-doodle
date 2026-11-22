package com.minidoodle.persistence.jpa;

import com.minidoodle.persistence.entity.MeetingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMeetingRepository extends JpaRepository<MeetingEntity, UUID> {
    List<MeetingEntity> findAllBySlotIdIn(List<UUID> slotIds);
    Optional<MeetingEntity> findByIdempotencyKey(String idempotencyKey);
}

