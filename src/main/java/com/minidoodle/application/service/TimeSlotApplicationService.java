package com.minidoodle.application.service;

import com.minidoodle.application.command.CreateTimeSlotCommand;
import com.minidoodle.application.command.UpdateTimeSlotCommand;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.exception.InvalidSlotOperationException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.TimeRange;
import com.minidoodle.domain.model.TimeSlot;
import com.minidoodle.domain.repository.CalendarRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeSlotApplicationService {

    private final CalendarRepository calendarRepository;
    private final ObservationRegistry observationRegistry;
    private final Counter slotsCreatedCounter;

    public TimeSlotApplicationService(CalendarRepository calendarRepository,
                                      ObservationRegistry observationRegistry,
                                      MeterRegistry meterRegistry) {
        this.calendarRepository = calendarRepository;
        this.observationRegistry = observationRegistry;
        this.slotsCreatedCounter = Counter.builder("doodle.slots.created")
                .description("Total number of time slots created")
                .register(meterRegistry);
    }

    @Transactional
    public TimeSlot createSlot(CreateTimeSlotCommand command) {
        return Observation.createNotStarted("doodle.slot.create", observationRegistry)
                .highCardinalityKeyValue("user.id", command.userId().toString())
                .observe(() -> {
                    TimeRange range = new TimeRange(command.startTime(), command.endTime());
                    Calendar calendar = calendarRepository.loadByUserIdAndWindow(command.userId(), range);
                    TimeSlot created = calendar.addSlot(range);
                    calendarRepository.save(calendar);
                    slotsCreatedCounter.increment();
                    return created;
                });
    }

    @Transactional
    public TimeSlot updateSlot(UpdateTimeSlotCommand command) {
        if ((command.startTime() == null) != (command.endTime() == null)) {
            throw new InvalidSlotOperationException(
                    "Both startTime and endTime must be provided together when updating time bounds"
            );
        }

        Calendar calendar = calendarRepository.loadBySlotId(command.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found: " + command.slotId()));

        ensureCalendarBelongsToUser(calendar, command.userId());

        TimeRange newRange = (command.startTime() != null)
                ? new TimeRange(command.startTime(), command.endTime())
                : null;

        TimeSlot updated = calendar.updateSlot(command.slotId(), newRange, command.status());
        calendarRepository.save(calendar);

        return updated;
    }


    @Transactional
    public void deleteSlot(UUID userId, UUID slotId) {
        Calendar calendar = calendarRepository.loadBySlotId(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found: " + slotId));

        ensureCalendarBelongsToUser(calendar, userId);

        TimeSlot removed = calendar.removeSlot(slotId);
        calendarRepository.deleteSlot(removed);
    }

    private void ensureCalendarBelongsToUser(Calendar calendar, UUID expectedUserId) {
        if (!calendar.getUserId().equals(expectedUserId)) {
            throw new InvalidSlotOperationException("Time slot does not belong to user: " + expectedUserId);
        }
    }
}
