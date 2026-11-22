package com.minidoodle.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minidoodle.domain.exception.InvalidSlotOperationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 12, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void shouldCreateValidTimeRangeAndCalculateDuration() {
        TimeRange range = new TimeRange(BASE, BASE.plusMinutes(90));

        assertThat(range.start()).isEqualTo(BASE);
        assertThat(range.end()).isEqualTo(BASE.plusMinutes(90));
        assertThat(range.durationMinutes()).isEqualTo(90L);
    }

    @Test
    void shouldRejectNullStartOrEndTime() {
        assertThatThrownBy(() -> new TimeRange(null, BASE.plusHours(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Start time");

        assertThatThrownBy(() -> new TimeRange(BASE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("End time");
    }

    @Test
    void shouldRejectEqualOrInvertedEndTime() {
        assertThatThrownBy(() -> new TimeRange(BASE, BASE))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("strictly after");

        assertThatThrownBy(() -> new TimeRange(BASE, BASE.minusMinutes(15)))
                .isInstanceOf(InvalidSlotOperationException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void shouldNotOverlapForBackToBackAdjacentRanges() {
        TimeRange first = new TimeRange(BASE, BASE.plusHours(1));
        TimeRange second = new TimeRange(BASE.plusHours(1), BASE.plusHours(2));

        assertThat(first.overlaps(second)).isFalse();
        assertThat(second.overlaps(first)).isFalse();
    }

    @Test
    void shouldDetectPartialEnclosingAndIdenticalOverlaps() {
        TimeRange tenToEleven = new TimeRange(BASE, BASE.plusHours(1));
        TimeRange tenThirtyToElevenThirty = new TimeRange(BASE.plusMinutes(30), BASE.plusMinutes(90));
        TimeRange nineToTwelve = new TimeRange(BASE.minusHours(1), BASE.plusHours(2));

        assertThat(tenToEleven.overlaps(tenThirtyToElevenThirty)).isTrue();
        assertThat(tenThirtyToElevenThirty.overlaps(tenToEleven)).isTrue();
        assertThat(tenToEleven.overlaps(nineToTwelve)).isTrue();
        assertThat(nineToTwelve.overlaps(tenToEleven)).isTrue();
        assertThat(tenToEleven.overlaps(tenToEleven)).isTrue();
    }
}
