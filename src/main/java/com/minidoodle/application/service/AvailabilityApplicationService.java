package com.minidoodle.application.service;

import com.minidoodle.domain.model.AvailabilitySummary;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.SlotStatus;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.repository.CalendarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AvailabilityApplicationService {

    private final CalendarRepository calendarRepository;

    public AvailabilityApplicationService(CalendarRepository calendarRepository) {
        this.calendarRepository = calendarRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilitySummary getUserAvailability(UUID userId,
                                                   OffsetDateTime from,
                                                   OffsetDateTime to,
                                                   SlotStatus filterStatus) {
        TimeRange window = new TimeRange(from, to);
        Calendar calendar = calendarRepository.loadByUserIdAndWindow(userId, window);
        return calendar.calculateAvailability(window, filterStatus);
    }
}
