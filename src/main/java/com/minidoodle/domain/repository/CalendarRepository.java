package com.minidoodle.domain.repository;

import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;

import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository {

    Calendar loadByUserIdAndWindow(UUID userId, TimeRange window);
    Optional<Calendar> loadBySlotId(UUID slotId);
    void save(Calendar calendar);
    void deleteSlot(TimeSlot slot);
}
