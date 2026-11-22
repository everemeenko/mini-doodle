package com.minidoodle.persistence.adapter;

import com.minidoodle.domain.model.Meeting;
import com.minidoodle.domain.repository.MeetingRepository;
import com.minidoodle.persistence.entity.MeetingEntity;
import com.minidoodle.persistence.jpa.SpringDataMeetingRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMeetingRepositoryAdapter implements MeetingRepository {

    private final SpringDataMeetingRepository jpaRepository;
    private final EntityManager entityManager;

    public JpaMeetingRepositoryAdapter(SpringDataMeetingRepository jpaRepository,
                                       EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void acquireIdempotencyLock(String idempotencyKey) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .setParameter("key", idempotencyKey)
                .getSingleResult();
    }

    @Override
    public Meeting save(Meeting meeting, String idempotencyKey, String requestHash) {
        MeetingEntity entity = new MeetingEntity(
                meeting.getId(),
                meeting.getSlotId(),
                meeting.getTitle(),
                meeting.getDescription(),
                idempotencyKey,
                requestHash,
                meeting.getParticipantEmails(),
                meeting.getCreatedAt()
        );
        MeetingEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<StoredIdempotentMeeting> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> new StoredIdempotentMeeting(toDomain(entity), entity.getRequestHash()));
    }

    @Override
    public List<Meeting> findAllBySlotIds(List<UUID> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllBySlotIdIn(slotIds).stream()
                .map(this::toDomain)
                .toList();
    }

    private Meeting toDomain(MeetingEntity entity) {
        return new Meeting(
                entity.getId(),
                entity.getSlotId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getParticipantEmails(),
                entity.getCreatedAt()
        );
    }
}
