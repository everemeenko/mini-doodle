-- Deterministic UUIDs for easy copy-pasting in Swagger UI, GraphiQL, and README curl examples
-- Alice: 11111111-1111-1111-1111-111111111111
-- Bob:   22222222-2222-2222-2222-222222222222
-- Carol: 33333333-3333-3333-3333-333333333333

INSERT INTO users (id, name, email) VALUES
                                        ('11111111-1111-1111-1111-111111111111', 'Alice Martin', 'alice@minidoodle.dev'),
                                        ('22222222-2222-2222-2222-222222222222', 'Bob Vance', 'bob@minidoodle.dev'),
                                        ('33333333-3333-3333-3333-333333333333', 'Carol Danvers', 'carol@minidoodle.dev')
    ON CONFLICT (id) DO NOTHING;

-- Alice's schedule for tomorrow (UTC):
-- 09:00 - 10:00 -> BOOKED (Architecture Review with Bob & Carol)
-- 10:00 - 11:00 -> FREE   (Back-to-back slot; ready to be booked by reviewers)
-- 11:30 - 12:30 -> BUSY   (Focus time / blocked)
-- 14:00 - 15:30 -> FREE   (Afternoon availability)
INSERT INTO time_slots (id, user_id, start_time, end_time, status, version) VALUES
                                                                                (
                                                                                    'aaaa1111-1111-1111-1111-111111111111',
                                                                                    '11111111-1111-1111-1111-111111111111',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '09:00:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '10:00:00') AT TIME ZONE 'UTC',
                                                                                    'BOOKED',
                                                                                    1
                                                                                ),
                                                                                (
                                                                                    'aaaa2222-2222-2222-2222-222222222222',
                                                                                    '11111111-1111-1111-1111-111111111111',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '10:00:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '11:00:00') AT TIME ZONE 'UTC',
                                                                                    'FREE',
                                                                                    0
                                                                                ),
                                                                                (
                                                                                    'aaaa3333-3333-3333-3333-333333333333',
                                                                                    '11111111-1111-1111-1111-111111111111',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '11:30:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '12:30:00') AT TIME ZONE 'UTC',
                                                                                    'BUSY',
                                                                                    0
                                                                                ),
                                                                                (
                                                                                    'aaaa4444-4444-4444-4444-444444444444',
                                                                                    '11111111-1111-1111-1111-111111111111',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '14:00:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '15:30:00') AT TIME ZONE 'UTC',
                                                                                    'FREE',
                                                                                    0
                                                                                )
    ON CONFLICT (id) DO NOTHING;

-- Bob's schedule for tomorrow (UTC):
-- 10:00 - 11:00 -> FREE
-- 13:00 - 14:00 -> FREE
INSERT INTO time_slots (id, user_id, start_time, end_time, status, version) VALUES
                                                                                (
                                                                                    'bbbb1111-1111-1111-1111-111111111111',
                                                                                    '22222222-2222-2222-2222-222222222222',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '10:00:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '11:00:00') AT TIME ZONE 'UTC',
                                                                                    'FREE',
                                                                                    0
                                                                                ),
                                                                                (
                                                                                    'bbbb2222-2222-2222-2222-222222222222',
                                                                                    '22222222-2222-2222-2222-222222222222',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '13:00:00') AT TIME ZONE 'UTC',
                                                                                    ((CURRENT_DATE + INTERVAL '1 day') + TIME '14:00:00') AT TIME ZONE 'UTC',
                                                                                    'FREE',
                                                                                    0
                                                                                )
    ON CONFLICT (id) DO NOTHING;

-- Meeting attached to Alice's 09:00 - 10:00 BOOKED slot
INSERT INTO meetings (id, slot_id, title, description) VALUES
    (
        '99991111-1111-1111-1111-111111111111',
        'aaaa1111-1111-1111-1111-111111111111',
        'Backend Architecture Review',
        'Reviewing Mini Doodle REST & GraphQL APIs, GiST constraints, and optimistic locking.'
    )
    ON CONFLICT (id) DO NOTHING;

INSERT INTO meeting_participants (meeting_id, participant_email) VALUES
                                                                     ('99991111-1111-1111-1111-111111111111', 'alice@minidoodle.dev'),
                                                                     ('99991111-1111-1111-1111-111111111111', 'bob@minidoodle.dev'),
                                                                     ('99991111-1111-1111-1111-111111111111', 'carol@minidoodle.dev')
    ON CONFLICT DO NOTHING;