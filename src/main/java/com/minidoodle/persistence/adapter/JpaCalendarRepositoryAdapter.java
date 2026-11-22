package com.minidoodle.persistence.adapter;

import com.minidoodle.domain.exception.SlotOverlapException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;
import com.minidoodle.domain.repository.CalendarRepository;
import com.minidoodle.persistence.entity.TimeSlotEntity;
import com.minidoodle.persistence.jpa.SpringDataTimeSlotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaCalendarRepositoryAdapter implements CalendarRepository {

    private final SpringDataTimeSlotRepository jpaRepository;

    public JpaCalendarRepositoryAdapter(SpringDataTimeSlotRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Calendar loadByUserIdAndWindow(UUID userId, TimeRange window) {
        List<TimeSlot> slots = jpaRepository
            .findOverlappingSlots(userId, window.start(), window.end())
            .stream()
            .map(this::toDomain)
            .toList();

        return new Calendar(userId, slots);
    }

    @Override
    public Optional<Calendar> loadBySlotId(UUID slotId) {
        return jpaRepository.findById(slotId).map(target -> {
            var searchStart = target.getStartTime().minusHours(24);
            var searchEnd = target.getEndTime().plusHours(24);

            List<TimeSlot> nearbySlots = jpaRepository
                .findOverlappingSlots(target.getUserId(), searchStart, searchEnd)
                .stream()
                .map(this::toDomain)
                .toList();

            return new Calendar(target.getUserId(), nearbySlots);
        });
    }

    @Override
    public void save(Calendar calendar) {
        try {
            for (TimeSlot slot : calendar.getSlots()) {
                if (slot.getVersion() == null) {
                    jpaRepository.saveAndFlush(toEntity(slot));
                } else {
                    TimeSlotEntity entity = jpaRepository.findById(slot.getId())
                        .orElseThrow(() -> new IllegalStateException("Slot disappeared during update: " + slot.getId()));
                    entity.setStartTime(slot.getTimeRange().start());
                    entity.setEndTime(slot.getTimeRange().end());
                    entity.setStatus(slot.getStatus());
                    jpaRepository.saveAndFlush(entity);
                }
            }
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapConstraintViolation(ex)) {
                throw new SlotOverlapException("Database prevented overlapping time slots for user: " + calendar.getUserId());
            }
            throw ex;
        }
    }

    @Override
    public void deleteSlotById(UUID slotId) {
        jpaRepository.deleteById(slotId);
    }

    private TimeSlot toDomain(TimeSlotEntity entity) {
        return new TimeSlot(
            entity.getId(),
            entity.getUserId(),
            new TimeRange(entity.getStartTime(), entity.getEndTime()),
            entity.getStatus(),
            entity.getVersion()
        );
    }

    private TimeSlotEntity toEntity(TimeSlot domain) {
        return new TimeSlotEntity(
            domain.getId(),
            domain.getUserId(),
            domain.getTimeRange().start(),
            domain.getTimeRange().end(),
            domain.getStatus(),
            domain.getVersion()
        );
    }

    private boolean isOverlapConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("excl_user_overlapping_slots")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
