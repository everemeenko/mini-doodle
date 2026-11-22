package com.minidoodle.domain.repository;

import com.minidoodle.domain.model.Meeting;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository {
    record StoredIdempotentMeeting(Meeting meeting, String requestHash) {}

    Meeting save(Meeting meeting, String idempotencyKey, String requestHash);
    Optional<StoredIdempotentMeeting> findByIdempotencyKey(String idempotencyKey);
    List<Meeting> findAllBySlotIds(List<UUID> slotIds);
}