package com.minidoodle.persistence.adapter;

import com.minidoodle.domain.exception.SlotOverlapException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;
import com.minidoodle.domain.repository.CalendarRepository;
import com.minidoodle.persistence.entity.TimeSlotEntity;
import com.minidoodle.persistence.jpa.SpringDataTimeSlotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

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
            OffsetDateTime windowStart = target.getStartTime().minusHours(24);
            OffsetDateTime windowEnd = target.getEndTime().plusHours(24);
            List<TimeSlot> nearbySlots = jpaRepository
                    .findOverlappingSlots(target.getUserId(), windowStart, windowEnd)
                    .stream()
                    .map(this::toDomain)
                    .toList();
            return new Calendar(target.getUserId(), nearbySlots);
        });
    }

    @Override
    public void save(Calendar calendar) {
        try {
            List<UUID> existingIds = calendar.getSlots().stream()
                    .filter(slot -> slot.getVersion() != null)
                    .map(TimeSlot::getId)
                    .toList();

            Map<UUID, TimeSlotEntity> managedById = existingIds.isEmpty()
                    ? Map.of()
                    : jpaRepository.findAllById(existingIds).stream()
                    .collect(Collectors.toMap(TimeSlotEntity::getId, Function.identity()));

            for (TimeSlot slot : calendar.getSlots()) {
                if (slot.getVersion() == null) {
                    TimeSlotEntity entity = new TimeSlotEntity(
                            slot.getId(),
                            slot.getUserId(),
                            slot.getTimeRange().start(),
                            slot.getTimeRange().end(),
                            slot.getStatus(),
                            slot.getVersion()
                    );
                    jpaRepository.save(entity);
                } else {
                    TimeSlotEntity existing = managedById.get(slot.getId());
                    if (existing == null || !Objects.equals(existing.getVersion(), slot.getVersion())) {
                        throw new ObjectOptimisticLockingFailureException(TimeSlotEntity.class, slot.getId());
                    }

                    boolean changed = !existing.getStartTime().isEqual(slot.getTimeRange().start())
                            || !existing.getEndTime().isEqual(slot.getTimeRange().end())
                            || existing.getStatus() != slot.getStatus();

                    if (changed) {
                        existing.update(
                                slot.getTimeRange().start(),
                                slot.getTimeRange().end(),
                                slot.getStatus()
                        );
                    }
                }
            }
            jpaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapConstraintViolation(ex)) {
                throw new SlotOverlapException("Database prevented overlapping time slots", ex);
            }
            throw ex;
        }
    }

    @Override
    public void deleteSlot(TimeSlot slot) {
        int deletedRows = jpaRepository.deleteByIdAndVersion(slot.getId(), slot.getVersion());
        if (deletedRows == 0) {
            throw new ObjectOptimisticLockingFailureException(TimeSlotEntity.class, slot.getId());
        }
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

    private boolean isOverlapConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("excl_user_overlapping_slots")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
