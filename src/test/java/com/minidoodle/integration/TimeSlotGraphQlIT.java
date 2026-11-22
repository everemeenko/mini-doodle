package com.minidoodle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

class TimeSlotGraphQlIT extends AbstractIntegrationTest {

    private static final UUID CAROL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private HttpGraphQlTester graphQlTester;

    @BeforeEach
    void setUpGraphQlTester() {
        WebTestClient webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/graphql")
                .build();
        this.graphQlTester = HttpGraphQlTester.create(webTestClient);
    }

    @Test
    void shouldCreateBookAndQuerySlotsWithNestedBatchMappedMeetings() {
        OffsetDateTime day = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(20).withHour(14).withMinute(0).withSecond(0).withNano(0);

        // 1. Create two slots via GraphQL mutation
        String slot1Id = graphQlTester.document("""
                mutation($input: CreateSlotInput!) {
                  createTimeSlot(input: $input) { id status }
                }
                """)
                .variable("input", Map.of(
                        "userId", CAROL_ID.toString(),
                        "startTime", day.toString(),
                        "endTime", day.plusHours(1).toString()
                ))
                .execute()
                .path("createTimeSlot.id").entity(String.class).get();

        String slot2Id = graphQlTester.document("""
                mutation($input: CreateSlotInput!) {
                  createTimeSlot(input: $input) { id status }
                }
                """)
                .variable("input", Map.of(
                        "userId", CAROL_ID.toString(),
                        "startTime", day.plusHours(1).toString(),
                        "endTime", day.plusHours(2).toString()
                ))
                .execute()
                .path("createTimeSlot.id").entity(String.class).get();

        // 2. Book slot1 via GraphQL mutation
        graphQlTester.document("""
                mutation($input: BookMeetingInput!) {
                  bookMeeting(input: $input) { id title participantEmails }
                }
                """)
                .variable("input", Map.of(
                        "slotId", slot1Id,
                        "title", "GraphQL Design Review",
                        "description", "Testing @BatchMapping",
                        "participants", List.of("carol@minidoodle.dev", "bob@minidoodle.dev")
                ))
                .execute()
                .path("bookMeeting.title").entity(String.class).isEqualTo("GraphQL Design Review");

        // 3. Query userAvailability including nested `meeting` on all slots (verifies @BatchMapping)
        graphQlTester.document("""
                query($userId: UUID!, $from: DateTime!, $to: DateTime!) {
                  userAvailability(userId: $userId, from: $from, to: $to) {
                    totalFreeMinutes
                    totalBookedMinutes
                    slots {
                      id
                      status
                      meeting {
                        title
                        participantEmails
                      }
                    }
                  }
                }
                """)
                .variable("userId", CAROL_ID.toString())
                .variable("from", day.toString())
                .variable("to", day.plusHours(3).toString())
                .execute()
                .path("userAvailability.totalFreeMinutes").entity(Integer.class).isEqualTo(60)
                .path("userAvailability.totalBookedMinutes").entity(Integer.class).isEqualTo(60)
                .path("userAvailability.slots[0].meeting.title").entity(String.class).isEqualTo("GraphQL Design Review")
                .path("userAvailability.slots[1].meeting").valueIsNull();

        // 4. Verify overlap conflict returns GraphQL error with CONFLICT extension code
        graphQlTester.document("""
                mutation($input: CreateSlotInput!) {
                  createTimeSlot(input: $input) { id }
                }
                """)
                .variable("input", Map.of(
                        "userId", CAROL_ID.toString(),
                        "startTime", day.plusMinutes(30).toString(),
                        "endTime", day.plusMinutes(90).toString()
                ))
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getExtensions()).containsEntry("code", "CONFLICT");
                });

        // 5. Verify deleting a BOOKED slot returns BAD_REQUEST GraphQL error
        graphQlTester.document("""
                mutation($userId: UUID!, $slotId: UUID!) {
                  deleteTimeSlot(userId: $userId, slotId: $slotId)
                }
                """)
                .variable("userId", CAROL_ID.toString())
                .variable("slotId", slot1Id)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                });

        // 6. Verify deleting a non-existent slot returns NOT_FOUND GraphQL error
        graphQlTester.document("""
                mutation($userId: UUID!, $slotId: UUID!) {
                  deleteTimeSlot(userId: $userId, slotId: $slotId)
                }
                """)
                .variable("userId", CAROL_ID.toString())
                .variable("slotId", UUID.randomUUID().toString())
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });

        // 7. Delete unbooked slot2 successfully
        graphQlTester.document("""
                mutation($userId: UUID!, $slotId: UUID!) {
                  deleteTimeSlot(userId: $userId, slotId: $slotId)
                }
                """)
                .variable("userId", CAROL_ID.toString())
                .variable("slotId", slot2Id)
                .execute()
                .path("deleteTimeSlot").entity(Boolean.class).isEqualTo(true);
    }
}
