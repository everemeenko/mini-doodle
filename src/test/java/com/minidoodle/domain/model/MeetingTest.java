package com.minidoodle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeetingTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 12, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldCreateMeetingWithImmutableParticipants() {
        UUID id = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Set<String> mutableEmails = new HashSet<>(Set.of("alice@minidoodle.dev", "bob@minidoodle.dev"));

        Meeting meeting = new Meeting(id, slotId, "Architecture Sync", "Discuss API", mutableEmails, NOW);
        mutableEmails.add("intruder@minidoodle.dev");

        assertThat(meeting.getId()).isEqualTo(id);
        assertThat(meeting.getSlotId()).isEqualTo(slotId);
        assertThat(meeting.getTitle()).isEqualTo("Architecture Sync");
        assertThat(meeting.getDescription()).isEqualTo("Discuss API");
        assertThat(meeting.getCreatedAt()).isEqualTo(NOW);
        assertThat(meeting.getParticipantEmails())
                .containsExactlyInAnyOrder("alice@minidoodle.dev", "bob@minidoodle.dev");

        assertThatThrownBy(() -> meeting.getParticipantEmails().add("another@minidoodle.dev"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullOrBlankTitle() {
        UUID id = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Set<String> participants = Set.of("alice@minidoodle.dev");

        assertThatThrownBy(() -> new Meeting(id, slotId, null, null, participants, NOW))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("title");

        assertThatThrownBy(() -> new Meeting(id, slotId, "   ", null, participants, NOW))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("title");
    }

    @Test
    void shouldRejectNullOrEmptyParticipants() {
        UUID id = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        assertThatThrownBy(() -> new Meeting(id, slotId, "Sync", null, null, NOW))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("at least one participant");

        assertThatThrownBy(() -> new Meeting(id, slotId, "Sync", null, Set.of(), NOW))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("at least one participant");
    }
}