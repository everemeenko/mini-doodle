package com.minidoodle.application.service;

import com.minidoodle.application.command.BookMeetingCommand;
import com.minidoodle.application.exception.IdempotencyConflictException;
import com.minidoodle.application.exception.ResourceNotFoundException;
import com.minidoodle.domain.model.Calendar;
import com.minidoodle.domain.model.Meeting;
import com.minidoodle.domain.repository.CalendarRepository;
import com.minidoodle.domain.repository.MeetingRepository;
import com.minidoodle.domain.repository.MeetingRepository.StoredIdempotentMeeting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MeetingApplicationService {

    private final CalendarRepository calendarRepository;
    private final MeetingRepository meetingRepository;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate requiresNewReadTx;
    private final ObservationRegistry observationRegistry;
    private final Counter bookingSuccessCounter;
    private final Counter bookingConflictCounter;
    private final Counter bookingReplayCounter;

    public MeetingApplicationService(CalendarRepository calendarRepository,
                                     MeetingRepository meetingRepository,
                                     PlatformTransactionManager transactionManager,
                                     ObservationRegistry observationRegistry,
                                     MeterRegistry meterRegistry) {
        this.calendarRepository = calendarRepository;
        this.meetingRepository = meetingRepository;

        this.writeTx = new TransactionTemplate(transactionManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.requiresNewReadTx = new TransactionTemplate(transactionManager);
        this.requiresNewReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewReadTx.setReadOnly(true);

        this.observationRegistry = observationRegistry;
        this.bookingSuccessCounter = Counter.builder("doodle.meetings.booked")
                .description("Total number of successfully booked meetings")
                .register(meterRegistry);
        this.bookingConflictCounter = Counter.builder("doodle.meetings.conflicts")
                .description("Total number of meeting booking conflicts")
                .register(meterRegistry);
        this.bookingReplayCounter = Counter.builder("doodle.meetings.idempotent_replays")
                .description("Total number of idempotent booking replays served")
                .register(meterRegistry);
    }

    public BookMeetingResult bookMeeting(BookMeetingCommand command) {
        String normalizedKey = normalizeKey(command.idempotencyKey());
        String requestHash = (normalizedKey != null) ? computeRequestHash(command) : null;

        return Observation.createNotStarted("doodle.meeting.book", observationRegistry)
                .lowCardinalityKeyValue("participants.count",
                        String.valueOf(command.participantEmails() != null ? command.participantEmails().size() : 0))
                .lowCardinalityKeyValue("idempotent", String.valueOf(normalizedKey != null))
                .highCardinalityKeyValue("slot.id", command.slotId().toString())
                .observe(() -> {
                    // 1. Fast-path check in an isolated read transaction
                    if (normalizedKey != null) {
                        Optional<StoredIdempotentMeeting> existing = findCommittedByKey(normalizedKey);
                        if (existing.isPresent()) {
                            return resolveExistingIdempotentBooking(existing.get(), requestHash);
                        }
                    }

                    // 2. Execute atomic booking in its own transaction
                    try {
                        Meeting created = writeTx.execute(status -> {
                            Calendar calendar = calendarRepository.loadBySlotId(command.slotId())
                                    .orElseThrow(() -> new ResourceNotFoundException("Time slot not found: " + command.slotId()));

                            Meeting meeting = calendar.scheduleMeeting(
                                    command.slotId(),
                                    command.title(),
                                    command.description(),
                                    command.participantEmails()
                            );

                            calendarRepository.save(calendar);
                            return meetingRepository.save(meeting, normalizedKey, requestHash);
                        });

                        bookingSuccessCounter.increment();
                        return new BookMeetingResult(created, true);

                    } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                        if (normalizedKey != null) {
                            // 3. Poll briefly in fresh REQUIRES_NEW snapshots until the racing winner's commit is visible
                            Optional<StoredIdempotentMeeting> winner = awaitCommittedWinner(normalizedKey);
                            if (winner.isPresent()) {
                                return resolveExistingIdempotentBooking(winner.get(), requestHash);
                            }
                            if (isIdempotencyKeyConstraintViolation(ex)) {
                                bookingConflictCounter.increment();
                                throw new IdempotencyConflictException(
                                        "Concurrent request with the same Idempotency-Key is already in progress or conflicted"
                                );
                            }
                        }
                        bookingConflictCounter.increment();
                        throw ex;
                    }
                });
    }

    @Transactional(readOnly = true)
    public Map<UUID, Meeting> findMeetingsBySlotIds(List<UUID> slotIds) {
        return meetingRepository.findAllBySlotIds(slotIds).stream()
                .collect(Collectors.toMap(Meeting::getSlotId, Function.identity()));
    }

    private Optional<StoredIdempotentMeeting> findCommittedByKey(String key) {
        return requiresNewReadTx.execute(status -> meetingRepository.findByIdempotencyKey(key));
    }

    private Optional<StoredIdempotentMeeting> awaitCommittedWinner(String key) {
        for (int attempt = 0; attempt < 4; attempt++) {
            Optional<StoredIdempotentMeeting> found = findCommittedByKey(key);
            if (found.isPresent()) {
                return found;
            }
            try {
                Thread.sleep(15L * (attempt + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    private BookMeetingResult resolveExistingIdempotentBooking(StoredIdempotentMeeting stored, String expectedHash) {
        if (!stored.requestHash().equals(expectedHash)) {
            bookingConflictCounter.increment();
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with a different booking payload"
            );
        }
        bookingReplayCounter.increment();
        return new BookMeetingResult(stored.meeting(), false);
    }

    private boolean isIdempotencyKeyConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && (msg.contains("meetings_idempotency_key_key") || msg.contains("idempotency_key"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeKey(String key) {
        return (key == null || key.isBlank()) ? null : key.trim();
    }

    private String computeRequestHash(BookMeetingCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String sortedParticipants = (command.participantEmails() == null)
                    ? ""
                    : command.participantEmails().stream().sorted().collect(Collectors.joining(","));
            String canonical = String.join("|",
                    String.valueOf(command.slotId()),
                    String.valueOf(command.title()),
                    String.valueOf(command.description()),
                    sortedParticipants
            );
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
