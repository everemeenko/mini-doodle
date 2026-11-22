package com.minidoodle.persistence.jpa;

import com.minidoodle.persistence.entity.TimeSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataTimeSlotRepository extends JpaRepository<TimeSlotEntity, UUID> {

    @Query("""
        SELECT s FROM TimeSlotEntity s
        WHERE s.userId = :userId
          AND s.startTime < :to
          AND s.endTime > :from
        ORDER BY s.startTime ASC
    """)
    List<TimeSlotEntity> findOverlappingSlots(
        @Param("userId") UUID userId,
        @Param("from") OffsetDateTime from,
        @Param("to") OffsetDateTime to
    );
}
